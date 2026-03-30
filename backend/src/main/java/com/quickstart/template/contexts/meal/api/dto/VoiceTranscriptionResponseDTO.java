package com.quickstart.template.contexts.meal.api.dto;

public class VoiceTranscriptionResponseDTO {
    private String text;
    private String provider;
    private Long durationMs;
    private Boolean emptyResult;

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public Boolean getEmptyResult() {
        return emptyResult;
    }

    public void setEmptyResult(Boolean emptyResult) {
        this.emptyResult = emptyResult;
    }
}
