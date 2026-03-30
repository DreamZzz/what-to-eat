package com.quickstart.template.platform.provider.speech;

import com.aliyuncs.CommonRequest;
import com.aliyuncs.CommonResponse;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.http.FormatType;
import com.aliyuncs.http.MethodType;
import com.aliyuncs.profile.DefaultProfile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quickstart.template.contexts.meal.application.SpeechTranscriptionException;
import com.quickstart.template.contexts.meal.application.VoiceTranscriptionResult;
import com.quickstart.template.contexts.media.application.InvalidMediaException;
import com.quickstart.template.contexts.media.application.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "app.speech.provider", havingValue = "aliyun")
public class AliyunSpeechTranscriptionProvider implements SpeechTranscriptionProvider {
    private static final Logger log = LoggerFactory.getLogger(AliyunSpeechTranscriptionProvider.class);
    private static final String PRODUCT = "nls-filetrans";
    private static final AudioFormat ALIYUN_TARGET_WAV_FORMAT = new AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            16_000,
            16,
            1,
            2,
            16_000,
            false
    );

    private final ObjectMapper objectMapper;
    private final FileStorageService fileStorageService;
    private final String regionId;
    private final String endpointName;
    private final String domain;
    private final String apiVersion;
    private final String accessKeyId;
    private final String accessKeySecret;
    private final String appKey;
    private final long pollIntervalMs;
    private final int maxPollAttempts;

    public AliyunSpeechTranscriptionProvider(
            ObjectMapper objectMapper,
            FileStorageService fileStorageService,
            @Value("${app.speech.aliyun.region-id:cn-beijing}") String regionId,
            @Value("${app.speech.aliyun.endpoint-name:cn-beijing}") String endpointName,
            @Value("${app.speech.aliyun.domain:filetrans.cn-beijing.aliyuncs.com}") String domain,
            @Value("${app.speech.aliyun.api-version:2018-08-17}") String apiVersion,
            @Value("${app.speech.aliyun.access-key-id:}") String accessKeyId,
            @Value("${app.speech.aliyun.access-key-secret:}") String accessKeySecret,
            @Value("${app.speech.aliyun.app-key:}") String appKey,
            @Value("${app.speech.aliyun.poll-interval-ms:1500}") long pollIntervalMs,
            @Value("${app.speech.aliyun.max-poll-attempts:20}") int maxPollAttempts) {
        this.objectMapper = objectMapper;
        this.fileStorageService = fileStorageService;
        this.regionId = regionId;
        this.endpointName = endpointName;
        this.domain = domain;
        this.apiVersion = apiVersion;
        this.accessKeyId = accessKeyId;
        this.accessKeySecret = accessKeySecret;
        this.appKey = appKey;
        this.pollIntervalMs = pollIntervalMs;
        this.maxPollAttempts = maxPollAttempts;
    }

    @Override
    public String providerName() {
        return "aliyun";
    }

    @Override
    public VoiceTranscriptionResult transcribe(MultipartFile audio, String locale) {
        validateConfig();

        try {
            String audioContentType = resolveAudioContentType(audio);
            PreparedAudioPayload preparedAudio = prepareAudioPayload(audio, audioContentType);
            FileStorageService.StoredFileDescriptor storedAudio = fileStorageService.storeManagedFile(
                    preparedAudio.bytes(),
                    preparedAudio.contentType(),
                    "speech",
                    audio.getOriginalFilename()
            );

            String taskId = submitTask(storedAudio.getPublicUrl(), locale);
            String text = pollResult(taskId);
            long durationMs = estimateDurationMs(audio);
            log.info("Aliyun speech transcription succeeded for taskId={} locale={}", taskId, locale);
            return new VoiceTranscriptionResult(text, providerName(), durationMs, text == null || text.isBlank());
        } catch (InvalidMediaException exception) {
            throw new SpeechTranscriptionException("录音文件格式无效，请重新录音", false, exception);
        } catch (SpeechTranscriptionException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("Aliyun speech transcription failed", exception);
            throw new SpeechTranscriptionException("语音识别失败，请稍后重试", false, exception);
        }
    }

    private PreparedAudioPayload prepareAudioPayload(MultipartFile audio, String audioContentType) throws Exception {
        byte[] originalBytes = audio.getBytes();
        if (!shouldNormalizeToAliyunWav(audio, audioContentType)) {
            return new PreparedAudioPayload(originalBytes, audioContentType);
        }

        byte[] normalizedBytes = normalizeWavToAliyunPreferredFormat(originalBytes);
        log.info(
                "Normalized wav upload for Aliyun ASR filename={} contentType={} originalSize={} normalizedSize={}",
                audio.getOriginalFilename(),
                audioContentType,
                originalBytes.length,
                normalizedBytes.length
        );
        return new PreparedAudioPayload(normalizedBytes, "audio/wav");
    }

    static byte[] normalizeWavToAliyunPreferredFormat(byte[] audioBytes) {
        try (
                AudioInputStream sourceStream = AudioSystem.getAudioInputStream(
                        new BufferedInputStream(new ByteArrayInputStream(audioBytes))
                );
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream()
        ) {
            try (AudioInputStream convertedStream = AudioSystem.getAudioInputStream(ALIYUN_TARGET_WAV_FORMAT, sourceStream)) {
                byte[] pcmBytes = convertedStream.readAllBytes();
                long frameLength = pcmBytes.length / ALIYUN_TARGET_WAV_FORMAT.getFrameSize();
                try (AudioInputStream normalizedStream = new AudioInputStream(
                        new ByteArrayInputStream(pcmBytes),
                        ALIYUN_TARGET_WAV_FORMAT,
                        frameLength
                )) {
                    AudioSystem.write(normalizedStream, AudioFileFormat.Type.WAVE, outputStream);
                }
            }
            return outputStream.toByteArray();
        } catch (UnsupportedAudioFileException | IllegalArgumentException exception) {
            throw new SpeechTranscriptionException("录音文件无法解析，请重新录音", false, exception);
        } catch (Exception exception) {
            throw new SpeechTranscriptionException("录音文件处理失败，请重新录音", false, exception);
        }
    }

    private boolean shouldNormalizeToAliyunWav(MultipartFile audio, String audioContentType) {
        String normalizedContentType = audioContentType == null ? "" : audioContentType.toLowerCase(Locale.ROOT);
        if ("audio/wav".equals(normalizedContentType) || "audio/x-wav".equals(normalizedContentType)) {
            return true;
        }

        String filename = audio.getOriginalFilename() == null ? "" : audio.getOriginalFilename().toLowerCase(Locale.ROOT);
        return filename.endsWith(".wav");
    }

    private void validateConfig() {
        if (regionId.isBlank() || endpointName.isBlank() || domain.isBlank() || apiVersion.isBlank()) {
            throw new SpeechTranscriptionException("语音识别服务未配置完整", true);
        }
        if (accessKeyId.isBlank() || accessKeySecret.isBlank() || appKey.isBlank()) {
            throw new SpeechTranscriptionException("语音识别服务凭据未配置完整", true);
        }
    }

    private String submitTask(String fileLink, String locale) {
        try {
            Map<String, Object> task = new LinkedHashMap<>();
            task.put("appkey", appKey);
            task.put("file_link", fileLink);
            task.put("version", "4.0");
            if (locale != null && locale.toLowerCase(Locale.ROOT).startsWith("en")) {
                task.put("language_hints", new String[]{"en"});
            }

            JsonNode response = invoke("SubmitTask", Map.of("Task", objectMapper.writeValueAsString(task)));
            String taskId = firstNonBlank(
                    response.path("TaskId").asText(null),
                    response.path("Data").path("TaskId").asText(null)
            );
            if (taskId == null || taskId.isBlank()) {
                throw new SpeechTranscriptionException("语音识别服务未返回任务编号", false);
            }
            return taskId;
        } catch (SpeechTranscriptionException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new SpeechTranscriptionException("提交语音识别任务失败", false, exception);
        }
    }

    private String pollResult(String taskId) {
        for (int attempt = 1; attempt <= maxPollAttempts; attempt++) {
            JsonNode response = invoke("GetTaskResult", Map.of("TaskId", taskId));
            String statusText = firstNonBlank(
                    response.path("StatusText").asText(null),
                    response.path("Data").path("StatusText").asText(null)
            );

            if (statusText == null || statusText.isBlank()) {
                throw new SpeechTranscriptionException("语音识别服务返回未知状态", false);
            }

            switch (statusText.toUpperCase(Locale.ROOT)) {
                case "SUCCESS":
                case "SUCCESS_WITH_NO_VALID_FRAGMENT":
                    return extractRecognizedText(response);
                case "RUNNING":
                case "QUEUEING":
                    sleepBeforeNextPoll();
                    break;
                default:
                    throw new SpeechTranscriptionException("语音识别任务失败: " + statusText, false);
            }
        }

        throw new SpeechTranscriptionException("语音识别超时，请稍后重试", false);
    }

    private JsonNode invoke(String action, Map<String, String> bodyParameters) {
        String rawResponse = null;
        try {
            DefaultProfile.addEndpoint(endpointName, regionId, PRODUCT, domain);
            IAcsClient client = new DefaultAcsClient(DefaultProfile.getProfile(regionId, accessKeyId, accessKeySecret));
            boolean queryMode = "GetTaskResult".equals(action);

            CommonRequest request = new CommonRequest();
            request.setSysMethod(queryMode ? MethodType.GET : MethodType.POST);
            request.setSysDomain(domain);
            request.setSysVersion(apiVersion);
            request.setSysAction(action);
            request.setSysProduct(PRODUCT);
            request.setSysAccept(FormatType.JSON);
            if (queryMode) {
                bodyParameters.forEach(request::putQueryParameter);
            } else {
                request.setHttpContentType(FormatType.FORM);
                bodyParameters.forEach(request::putBodyParameter);
            }

            CommonResponse response = client.getCommonResponse(request);
            rawResponse = response == null ? null : response.getData();
            if (rawResponse == null || rawResponse.isBlank()) {
                throw new SpeechTranscriptionException("语音识别服务返回空响应", false);
            }

            JsonNode payload = objectMapper.readTree(rawResponse);
            if (payload.path("StatusCode").asInt(20000000) != 20000000
                    && payload.path("StatusText").asText("").isBlank()) {
                String errorMessage = firstNonBlank(
                        payload.path("Message").asText(null),
                        payload.path("statusText").asText(null),
                        payload.toString()
                );
                throw new SpeechTranscriptionException("语音识别服务调用失败: " + errorMessage, false);
            }
            return payload;
        } catch (SpeechTranscriptionException exception) {
            throw exception;
        } catch (ClientException exception) {
            log.error("Aliyun speech invoke client error for action={}", action, exception);
            throw new SpeechTranscriptionException("语音识别服务调用失败", false, exception);
        } catch (Exception exception) {
            log.error(
                    "Aliyun speech invoke parse failure for action={} rawResponse={}",
                    action,
                    abbreviate(rawResponse),
                    exception
            );
            throw new SpeechTranscriptionException("语音识别服务解析失败", false, exception);
        }
    }

    private String extractRecognizedText(JsonNode response) {
        JsonNode resultNode = response.path("Result");
        if (resultNode.isMissingNode() || resultNode.isNull()) {
            resultNode = response.path("Data").path("Result");
        }
        if (resultNode.isTextual()) {
            try {
                resultNode = objectMapper.readTree(resultNode.asText());
            } catch (Exception ignored) {
                String plainText = resultNode.asText("");
                return plainText.trim();
            }
        }

        StringBuilder builder = new StringBuilder();
        JsonNode sentencesNode = resultNode.path("Sentences");
        if (sentencesNode.isArray()) {
            for (JsonNode sentenceNode : sentencesNode) {
                String sentenceText = firstNonBlank(
                        sentenceNode.path("Text").asText(null),
                        sentenceNode.path("text").asText(null)
                );
                if (sentenceText != null && !sentenceText.isBlank()) {
                    if (builder.length() > 0) {
                        builder.append(' ');
                    }
                    builder.append(sentenceText.trim());
                }
            }
        }

        if (builder.length() > 0) {
            return builder.toString();
        }

        String plainText = firstNonBlank(
                resultNode.path("Text").asText(""),
                resultNode.path("text").asText(""),
                response.path("Result").asText("")
        );
        return plainText == null ? "" : plainText.trim();
    }

    private String resolveAudioContentType(MultipartFile audio) {
        String contentType = audio.getContentType();
        if (contentType != null && !contentType.isBlank()) {
            return contentType;
        }

        String filename = audio.getOriginalFilename() == null ? "" : audio.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (filename.endsWith(".m4a")) {
            return "audio/mp4";
        }
        if (filename.endsWith(".aac")) {
            return "audio/aac";
        }
        if (filename.endsWith(".wav")) {
            return "audio/wav";
        }
        if (filename.endsWith(".mp3")) {
            return "audio/mpeg";
        }
        return "application/octet-stream";
    }

    private long estimateDurationMs(MultipartFile audio) {
        if (audio == null || audio.isEmpty()) {
            return 0L;
        }
        return Math.max(1000L, Math.min(60000L, audio.getSize() * 12));
    }

    private void sleepBeforeNextPoll() {
        try {
            Thread.sleep(pollIntervalMs);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new SpeechTranscriptionException("语音识别任务被中断", false, exception);
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String abbreviate(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 400) {
            return normalized;
        }
        return normalized.substring(0, 400) + "...";
    }

    private record PreparedAudioPayload(byte[] bytes, String contentType) {
    }
}
