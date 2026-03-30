package com.quickstart.template.contexts.media.api;

import com.quickstart.template.contexts.media.api.dto.FileUploadResponse;
import com.quickstart.template.contexts.media.application.FileStorageService;
import com.quickstart.template.contexts.media.application.InvalidMediaException;
import com.quickstart.template.platform.security.CurrentUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/uploads")
@Tag(name = "File Upload", description = "File upload APIs for images and media")
public class FileUploadController {

    private final FileStorageService fileStorageService;
    private final CurrentUserService currentUserService;

    @Autowired
    public FileUploadController(FileStorageService fileStorageService, CurrentUserService currentUserService) {
        this.fileStorageService = fileStorageService;
        this.currentUserService = currentUserService;
    }

    @PostMapping("/single")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    @Operation(summary = "Upload a single file", description = "Upload a single file (image or video) to the server")
    public ResponseEntity<FileUploadResponse> uploadSingleFile(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(new FileUploadResponse());
        }

        Long currentUserId = currentUserService.getCurrentUser().map(user -> user.getId()).orElse(null);
        if (currentUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new FileUploadResponse());
        }

        try {
            String fileName = fileStorageService.storeFile(file, currentUserId);
            String fileUrl = fileStorageService.getFileUrlFromFileName(fileName);
            
            FileUploadResponse response = new FileUploadResponse(
                fileName,
                fileUrl,
                file.getContentType(),
                file.getSize()
            );
            
            return ResponseEntity.ok(response);
        } catch (InvalidMediaException exception) {
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(new FileUploadResponse());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new FileUploadResponse());
        }
    }

    @PostMapping("/multiple")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    @Operation(summary = "Upload multiple files", description = "Upload multiple files (images or videos) to the server")
    public ResponseEntity<List<FileUploadResponse>> uploadMultipleFiles(@RequestParam("files") MultipartFile[] files) {
        if (files == null || files.length == 0) {
            return ResponseEntity.badRequest().body(List.of());
        }

        Long currentUserId = currentUserService.getCurrentUser().map(user -> user.getId()).orElse(null);
        if (currentUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(List.of());
        }

        try {
            List<FileUploadResponse> responses = Arrays.stream(files)
                    .map(file -> {
                        String fileName = fileStorageService.storeFile(file, currentUserId);
                        String fileUrl = fileStorageService.getFileUrlFromFileName(fileName);
                        return new FileUploadResponse(
                                fileName,
                                fileUrl,
                                file.getContentType(),
                                file.getSize()
                        );
                    })
                    .collect(Collectors.toList());
            
            return ResponseEntity.ok(responses);
        } catch (InvalidMediaException exception) {
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(List.of());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(List.of());
        }
    }

    @DeleteMapping("/{fileName}")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    @Operation(summary = "Delete a file", description = "Delete a previously uploaded file")
    public ResponseEntity<Void> deleteFile(@PathVariable String fileName) {
        Long currentUserId = currentUserService.getCurrentUser().map(user -> user.getId()).orElse(null);
        if (currentUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (!fileStorageService.isOwnedBy(fileName, currentUserId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        boolean deleted = fileStorageService.deleteFile(fileName, currentUserId);
        if (deleted) {
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}
