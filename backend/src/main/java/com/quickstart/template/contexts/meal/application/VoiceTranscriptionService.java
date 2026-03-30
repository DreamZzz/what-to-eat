package com.quickstart.template.contexts.meal.application;

import com.quickstart.template.contexts.meal.api.dto.VoiceTranscriptionResponseDTO;
import com.quickstart.template.platform.provider.speech.SpeechTranscriptionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class VoiceTranscriptionService {
    private static final long MAX_AUDIO_DURATION_MS = 60_000L;
    private static final Logger log = LoggerFactory.getLogger(VoiceTranscriptionService.class);

    private final SpeechTranscriptionProvider speechTranscriptionProvider;

    public VoiceTranscriptionService(SpeechTranscriptionProvider speechTranscriptionProvider) {
        this.speechTranscriptionProvider = speechTranscriptionProvider;
    }

    public VoiceTranscriptionResponseDTO transcribe(MultipartFile audio, String locale) {
        validateAudio(audio);
        log.info("Voice transcription using provider={}", speechTranscriptionProvider.providerName());
        VoiceTranscriptionResult result = speechTranscriptionProvider.transcribe(audio, normalizeLocale(locale));
        VoiceTranscriptionResponseDTO response = new VoiceTranscriptionResponseDTO();
        response.setText(result.getText());
        response.setProvider(result.getProvider());
        response.setDurationMs(Math.min(MAX_AUDIO_DURATION_MS, result.getDurationMs() == null ? 0L : result.getDurationMs()));
        response.setEmptyResult(result.getEmptyResult());
        return response;
    }

    private void validateAudio(MultipartFile audio) {
        if (audio == null || audio.isEmpty()) {
            throw new SpeechTranscriptionException("音频文件不能为空", false);
        }

        String contentType = audio.getContentType();
        String filename = audio.getOriginalFilename() == null ? "" : audio.getOriginalFilename().toLowerCase();
        boolean supportedContentType = contentType != null && (
                contentType.startsWith("audio/")
                        || "application/octet-stream".equalsIgnoreCase(contentType)
        );
        boolean supportedExtension = filename.endsWith(".m4a")
                || filename.endsWith(".aac")
                || filename.endsWith(".wav")
                || filename.endsWith(".mp3");

        if (!supportedContentType && !supportedExtension) {
            throw new SpeechTranscriptionException("不支持的音频格式", false);
        }
    }

    private String normalizeLocale(String locale) {
        if (locale == null || locale.isBlank()) {
            return "zh-CN";
        }
        return locale;
    }

    public String providerName() {
        return speechTranscriptionProvider.providerName();
    }
}
