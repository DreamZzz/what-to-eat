package com.quickstart.template.platform.provider.speech;

import com.quickstart.template.contexts.meal.application.VoiceTranscriptionResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

@Component
@ConditionalOnProperty(name = "app.speech.provider", havingValue = "mock", matchIfMissing = true)
public class MockSpeechTranscriptionProvider implements SpeechTranscriptionProvider {

    @Override
    public String providerName() {
        return "mock";
    }

    @Override
    public VoiceTranscriptionResult transcribe(MultipartFile audio, String locale) {
        long durationMs = estimateDurationMs(audio);
        String text = buildMockText(audio, locale);
        return new VoiceTranscriptionResult(text, providerName(), durationMs, text == null || text.isBlank());
    }

    private long estimateDurationMs(MultipartFile audio) {
        if (audio == null || audio.isEmpty()) {
            return 0L;
        }
        return Math.max(1000L, Math.min(60000L, audio.getSize() * 12));
    }

    private String buildMockText(MultipartFile audio, String locale) {
        String filename = audio != null ? audio.getOriginalFilename() : null;
        if (filename != null && filename.toLowerCase().contains("spicy")) {
            return "麻辣香锅";
        }
        if (filename != null && filename.toLowerCase().contains("noodle")) {
            return "番茄鸡蛋面";
        }
        if (locale != null && locale.toLowerCase().startsWith("en")) {
            return "rice bowl with seasonal vegetables";
        }
        return new String("番茄鸡蛋面".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
    }
}
