package com.quickstart.template.contexts.meal.application;

public class VoiceTranscriptionResult {
    private final String text;
    private final String provider;
    private final Long durationMs;
    private final Boolean emptyResult;

    public VoiceTranscriptionResult(String text, String provider, Long durationMs, Boolean emptyResult) {
        this.text = text;
        this.provider = provider;
        this.durationMs = durationMs;
        this.emptyResult = emptyResult;
    }

    public String getText() {
        return text;
    }

    public String getProvider() {
        return provider;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public Boolean getEmptyResult() {
        return emptyResult;
    }
}
