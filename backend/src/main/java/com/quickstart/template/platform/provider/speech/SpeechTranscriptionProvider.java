package com.quickstart.template.platform.provider.speech;

import com.quickstart.template.contexts.meal.application.VoiceTranscriptionResult;
import org.springframework.web.multipart.MultipartFile;

public interface SpeechTranscriptionProvider {
    String providerName();

    VoiceTranscriptionResult transcribe(MultipartFile audio, String locale);
}
