package com.quickstart.template.contexts.meal.api;

import com.quickstart.template.contexts.account.domain.User;
import com.quickstart.template.contexts.meal.api.dto.VoiceTranscriptionResponseDTO;
import com.quickstart.template.contexts.meal.application.SpeechTranscriptionException;
import com.quickstart.template.contexts.meal.application.VoiceTranscriptionService;
import com.quickstart.template.platform.security.CurrentUserService;
import com.quickstart.template.shared.dto.MessageResponse;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.validation.annotation.Validated;

import java.util.Optional;

@RestController
@RequestMapping("/api/voice")
@PreAuthorize("isAuthenticated()")
@Validated
public class VoiceTranscriptionController {
    private final VoiceTranscriptionService voiceTranscriptionService;
    private final CurrentUserService currentUserService;

    public VoiceTranscriptionController(VoiceTranscriptionService voiceTranscriptionService, CurrentUserService currentUserService) {
        this.voiceTranscriptionService = voiceTranscriptionService;
        this.currentUserService = currentUserService;
    }

    @PostMapping(value = "/transcriptions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> transcribe(
            @RequestParam("audio") MultipartFile audio,
            @RequestParam(value = "locale", required = false, defaultValue = "zh-CN")
            @Pattern(regexp = "^[A-Za-z]{2,3}(-[A-Za-z]{2})?$|^zh-CN$") String locale) {
        Optional<Long> currentUserId = getCurrentUserId();
        if (currentUserId.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new MessageResponse("Authentication required"));
        }

        try {
            VoiceTranscriptionResponseDTO response = voiceTranscriptionService.transcribe(audio, locale);
            return ResponseEntity.ok(response);
        } catch (SpeechTranscriptionException exception) {
            HttpStatus status = exception.isConfiguration() ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_REQUEST;
            if (!exception.isConfiguration() && "音频文件不能为空".equals(exception.getMessage())) {
                status = HttpStatus.BAD_REQUEST;
            }
            return ResponseEntity.status(status).body(new MessageResponse(
                    exception.getMessage(),
                    "speech",
                    voiceTranscriptionService.providerName(),
                    exception.isConfiguration()
            ));
        }
    }

    private Optional<Long> getCurrentUserId() {
        return currentUserService.getCurrentUser().map(User::getId);
    }
}
