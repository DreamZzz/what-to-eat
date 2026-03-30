package com.quickstart.template.contexts.media.application;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.CannedAccessControlList;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import com.quickstart.template.platform.config.OssConfig.OssProperties;
import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class FileStorageService {
    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);
    private static final Map<String, String> IMAGE_EXTENSIONS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/gif", "gif",
            "image/bmp", "bmp"
    );
    private static final Map<String, String> VIDEO_EXTENSIONS = Map.of(
            "video/mp4", "mp4",
            "video/quicktime", "mov",
            "video/x-m4v", "m4v",
            "video/3gpp", "3gp",
            "video/webm", "webm"
    );
    private static final Map<String, String> AUDIO_EXTENSIONS = Map.of(
            "audio/mp4", "m4a",
            "audio/x-m4a", "m4a",
            "audio/aac", "aac",
            "audio/wav", "wav",
            "audio/x-wav", "wav",
            "audio/mpeg", "mp3",
            "audio/webm", "webm"
    );

    private final OSS ossClient;
    private final OssProperties ossProperties;
    private final Path tempUploadDir;

    @Value("${app.image.compression.max-width:1200}")
    private int maxWidth;

    @Value("${app.image.compression.max-height:1200}")
    private int maxHeight;

    @Value("${app.image.compression.quality:0.8}")
    private float compressionQuality;

    @Value("${app.image.compression.enabled:true}")
    private boolean compressionEnabled;

    @Value("${app.media.storage.provider:local}")
    private String storageProvider;

    private static class CompressedImageResult {
        final byte[] data;
        final long size;
        final String contentType;

        CompressedImageResult(byte[] data, String contentType) {
            this.data = data;
            this.size = data.length;
            this.contentType = contentType;
        }
    }

    private static class PreparedUpload {
        final byte[] data;
        final long size;
        final String contentType;
        final String fileExtension;

        PreparedUpload(byte[] data, long size, String contentType, String fileExtension) {
            this.data = data;
            this.size = size;
            this.contentType = contentType;
            this.fileExtension = fileExtension;
        }

        InputStream openStream(MultipartFile originalFile) throws IOException {
            if (data != null) {
                return new ByteArrayInputStream(data);
            }
            return originalFile.getInputStream();
        }
    }

    public static class StoredFileDescriptor {
        private final String storageKey;
        private final String publicUrl;
        private final String contentType;
        private final long size;

        public StoredFileDescriptor(String storageKey, String publicUrl, String contentType, long size) {
            this.storageKey = storageKey;
            this.publicUrl = publicUrl;
            this.contentType = contentType;
            this.size = size;
        }

        public String getStorageKey() {
            return storageKey;
        }

        public String getPublicUrl() {
            return publicUrl;
        }

        public String getContentType() {
            return contentType;
        }

        public long getSize() {
            return size;
        }
    }

    public FileStorageService(
            ObjectProvider<OSS> ossClientProvider,
            ObjectProvider<OssProperties> ossPropertiesProvider,
            @Value("${app.upload-dir}") String uploadDir) {
        this.ossClient = ossClientProvider.getIfAvailable();
        this.ossProperties = ossPropertiesProvider.getIfAvailable();
        this.tempUploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.tempUploadDir);
        } catch (IOException e) {
            throw new RuntimeException("Could not create temporary upload directory", e);
        }
    }

    public String storeFile(MultipartFile file, Long ownerId) {
        if (file == null || file.isEmpty()) {
            throw new InvalidMediaException("Uploaded file is empty");
        }
        if (ownerId == null) {
            throw new IllegalArgumentException("Uploader is required");
        }

        PreparedUpload preparedUpload;
        try {
            preparedUpload = prepareUpload(file);
        } catch (IOException exception) {
            throw new RuntimeException("Failed to inspect uploaded media", exception);
        }

        String fileName = buildStorageKey(ownerId, preparedUpload.fileExtension);
        try {
            if (useOssStorage()) {
                storeFileInOss(file, fileName, preparedUpload);
            } else {
                storeFileLocally(file, fileName, preparedUpload);
            }
            return fileName;
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file " + fileName, e);
        }
    }

    public StoredFileDescriptor storeManagedFile(byte[] data, String contentType, String namespace, String fileNameHint) {
        if (data == null || data.length == 0) {
            throw new InvalidMediaException("Managed file payload is empty");
        }

        PreparedUpload preparedUpload;
        try {
            preparedUpload = prepareManagedUpload(data, contentType);
        } catch (IOException exception) {
            throw new RuntimeException("Failed to inspect managed media", exception);
        }

        String storageKey = buildManagedStorageKey(namespace, fileNameHint, preparedUpload.fileExtension);
        try {
            if (useOssStorage()) {
                storeFileInOss(null, storageKey, preparedUpload);
            } else {
                storeFileLocally(null, storageKey, preparedUpload);
            }
        } catch (IOException exception) {
            throw new RuntimeException("Failed to store managed file " + storageKey, exception);
        }

        return new StoredFileDescriptor(
                storageKey,
                getFileUrlFromFileName(storageKey),
                preparedUpload.contentType,
                preparedUpload.size
        );
    }

    public boolean isOwnedBy(String fileName, Long ownerId) {
        if (fileName == null || fileName.isBlank() || ownerId == null) {
            return false;
        }

        String expectedPrefix = "user-" + ownerId + "__";
        return fileName.startsWith(expectedPrefix);
    }

    public boolean deleteFile(String fileName, Long ownerId) {
        if (!isOwnedBy(fileName, ownerId)) {
            return false;
        }

        try {
            if (useOssStorage()) {
                ossClient.deleteObject(ossProperties.getBucketName(), fileName);
            } else {
                Path targetPath = tempUploadDir.resolve(fileName).normalize();
                if (!targetPath.startsWith(tempUploadDir)) {
                    return false;
                }
                Files.deleteIfExists(targetPath);
            }
            return true;
        } catch (Exception e) {
            log.warn("Failed to delete file {}", fileName, e);
            return false;
        }
    }

    public String getFileUrlFromFileName(String fileName) {
        if (useOssStorage()) {
            return getFileUrl(fileName);
        }
        return "/uploads/images/" + fileName;
    }

    private boolean useOssStorage() {
        return "oss".equalsIgnoreCase(storageProvider) && ossClient != null && ossProperties != null;
    }

    private String buildStorageKey(Long ownerId, String extension) {
        return "user-" + ownerId + "__" + UUID.randomUUID() + "." + extension;
    }

    private String buildManagedStorageKey(String namespace, String fileNameHint, String extension) {
        String normalizedNamespace = sanitizeKeySegment(namespace, "system");
        String normalizedHint = sanitizeKeySegment(fileNameHint, "file");
        return "managed/" + normalizedNamespace + "/" + normalizedHint + "__" + UUID.randomUUID() + "." + extension;
    }

    private PreparedUpload prepareUpload(MultipartFile file) throws IOException {
        String normalizedContentType = normalizeContentType(file.getContentType());
        if (IMAGE_EXTENSIONS.containsKey(normalizedContentType)) {
            CompressedImageResult compressedResult = compressImage(file, normalizedContentType);
            return new PreparedUpload(
                    compressedResult.data,
                    compressedResult.size,
                    compressedResult.contentType,
                    extensionForContentType(compressedResult.contentType)
            );
        }

        if (VIDEO_EXTENSIONS.containsKey(normalizedContentType)) {
            String validatedContentType = validateVideoSignature(file, normalizedContentType);
            return new PreparedUpload(
                    null,
                    file.getSize(),
                    validatedContentType,
                    extensionForContentType(validatedContentType)
            );
        }

        throw new InvalidMediaException("Only JPEG, PNG, GIF, BMP, MP4, MOV, M4V, 3GP and WebM uploads are supported");
    }

    private PreparedUpload prepareManagedUpload(byte[] data, String contentType) throws IOException {
        String normalizedContentType = normalizeContentType(contentType);
        if (IMAGE_EXTENSIONS.containsKey(normalizedContentType) || looksLikeImage(data)) {
            CompressedImageResult compressedResult = compressImage(data, normalizedContentType);
            return new PreparedUpload(
                    compressedResult.data,
                    compressedResult.size,
                    compressedResult.contentType,
                    extensionForContentType(compressedResult.contentType)
            );
        }

        if (AUDIO_EXTENSIONS.containsKey(normalizedContentType)) {
            return new PreparedUpload(
                    data,
                    data.length,
                    normalizedContentType,
                    AUDIO_EXTENSIONS.get(normalizedContentType)
            );
        }

        throw new InvalidMediaException("Unsupported managed media type: " + normalizedContentType);
    }

    private void storeFileInOss(MultipartFile file, String fileName, PreparedUpload preparedUpload) throws IOException {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType(preparedUpload.contentType);
        metadata.setContentLength(preparedUpload.size);
        metadata.setObjectAcl(CannedAccessControlList.PublicRead);

        try (InputStream inputStream = preparedUpload.openStream(file)) {
            PutObjectRequest putObjectRequest = new PutObjectRequest(
                    ossProperties.getBucketName(),
                    fileName,
                    inputStream,
                    metadata
            );
            ossClient.putObject(putObjectRequest);
        }
    }

    private void storeFileLocally(MultipartFile file, String fileName, PreparedUpload preparedUpload) throws IOException {
        Path targetPath = tempUploadDir.resolve(fileName).normalize();
        if (!targetPath.startsWith(tempUploadDir)) {
            throw new IOException("Invalid file path");
        }

        Files.createDirectories(targetPath.getParent());
        try (InputStream inputStream = preparedUpload.openStream(file)) {
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private CompressedImageResult compressImage(MultipartFile file, String originalContentType) throws IOException {
        try (InputStream originalStream = file.getInputStream()) {
            BufferedImage originalImage = ImageIO.read(originalStream);
            return compressDecodedImage(originalImage, originalContentType);
        }
    }

    private CompressedImageResult compressImage(byte[] data, String originalContentType) throws IOException {
        try (InputStream inputStream = new ByteArrayInputStream(data)) {
            BufferedImage originalImage = ImageIO.read(inputStream);
            return compressDecodedImage(originalImage, originalContentType);
        }
    }

    private CompressedImageResult compressDecodedImage(BufferedImage originalImage, String originalContentType) throws IOException {
        if (originalImage == null) {
            throw new InvalidMediaException("Uploaded image content could not be decoded");
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        String outputFormat = getImageFormat(originalContentType);

        if (!compressionEnabled || (originalImage.getWidth() <= maxWidth && originalImage.getHeight() <= maxHeight)) {
            Thumbnails.of(originalImage)
                    .scale(1.0)
                    .outputQuality(compressionQuality)
                    .outputFormat(outputFormat)
                    .toOutputStream(outputStream);
        } else {
            Thumbnails.of(originalImage)
                    .size(maxWidth, maxHeight)
                    .keepAspectRatio(true)
                    .outputQuality(compressionQuality)
                    .outputFormat(outputFormat)
                    .toOutputStream(outputStream);
        }

        return new CompressedImageResult(outputStream.toByteArray(), contentTypeForImageFormat(outputFormat));
    }

    private String validateVideoSignature(MultipartFile file, String declaredContentType) throws IOException {
        byte[] header = readPrefix(file, 16);
        if (header.length < 4) {
            throw new InvalidMediaException("Uploaded video file is too small");
        }

        if (isWebm(header)) {
            if (!"video/webm".equals(declaredContentType)) {
                throw new InvalidMediaException("Video content does not match declared media type");
            }
            return "video/webm";
        }

        if (!isIsoBaseMediaFile(header)) {
            throw new InvalidMediaException("Unsupported or malformed video container");
        }

        String brand = header.length >= 12
                ? new String(header, 8, 4, java.nio.charset.StandardCharsets.US_ASCII).toLowerCase(Locale.ROOT)
                : "";

        if (brand.startsWith("qt")) {
            if (!"video/quicktime".equals(declaredContentType)) {
                throw new InvalidMediaException("Video content does not match declared media type");
            }
            return "video/quicktime";
        }

        if (brand.startsWith("m4v")) {
            if (!"video/x-m4v".equals(declaredContentType) && !"video/mp4".equals(declaredContentType)) {
                throw new InvalidMediaException("Video content does not match declared media type");
            }
            return "video/x-m4v";
        }

        if (brand.startsWith("3gp") || brand.startsWith("3g2")) {
            if (!"video/3gpp".equals(declaredContentType)) {
                throw new InvalidMediaException("Video content does not match declared media type");
            }
            return "video/3gpp";
        }

        if (!"video/mp4".equals(declaredContentType) && !"video/x-m4v".equals(declaredContentType)) {
            throw new InvalidMediaException("Video content does not match declared media type");
        }
        return "video/mp4";
    }

    private byte[] readPrefix(MultipartFile file, int length) throws IOException {
        try (InputStream inputStream = file.getInputStream()) {
            return inputStream.readNBytes(length);
        }
    }

    private boolean isWebm(byte[] header) {
        return header.length >= 4
                && (header[0] & 0xFF) == 0x1A
                && (header[1] & 0xFF) == 0x45
                && (header[2] & 0xFF) == 0xDF
                && (header[3] & 0xFF) == 0xA3;
    }

    private boolean isIsoBaseMediaFile(byte[] header) {
        return header.length >= 8
                && header[4] == 'f'
                && header[5] == 't'
                && header[6] == 'y'
                && header[7] == 'p';
    }

    private boolean looksLikeImage(byte[] data) {
        try (InputStream inputStream = new ByteArrayInputStream(data)) {
            return ImageIO.read(inputStream) != null;
        } catch (IOException exception) {
            return false;
        }
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null) {
            return "";
        }
        return contentType.split(";")[0].trim().toLowerCase(Locale.ROOT);
    }

    private String extensionForContentType(String contentType) {
        if (IMAGE_EXTENSIONS.containsKey(contentType)) {
            return IMAGE_EXTENSIONS.get(contentType);
        }
        if (AUDIO_EXTENSIONS.containsKey(contentType)) {
            return AUDIO_EXTENSIONS.get(contentType);
        }
        if (VIDEO_EXTENSIONS.containsKey(contentType)) {
            return VIDEO_EXTENSIONS.get(contentType);
        }
        throw new InvalidMediaException("Unsupported media type: " + contentType);
    }

    private String getImageFormat(String contentType) {
        return switch (contentType) {
            case "image/png" -> "png";
            case "image/gif" -> "gif";
            case "image/bmp" -> "bmp";
            default -> "jpg";
        };
    }

    private String contentTypeForImageFormat(String format) {
        return switch (format) {
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "bmp" -> "image/bmp";
            default -> "image/jpeg";
        };
    }

    private String sanitizeKeySegment(String input, String fallback) {
        if (input == null || input.isBlank()) {
            return fallback;
        }

        String normalized = input.trim().toLowerCase(Locale.ROOT)
                .replaceAll("\\.[^.]+$", "")
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        return normalized.isBlank() ? fallback : normalized;
    }

    private String getFileUrl(String objectKey) {
        String cdnDomain = ossProperties.getCdnDomain();
        if (cdnDomain != null && !cdnDomain.trim().isEmpty()) {
            return "https://" + cdnDomain + "/" + objectKey;
        }

        String endpoint = ossProperties.getEndpoint().replace("https://", "").replace("http://", "");
        return "https://" + ossProperties.getBucketName() + "." + endpoint + "/" + objectKey;
    }
}
