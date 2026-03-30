package com.quickstart.template.contexts.meal.application;

public class SpeechTranscriptionException extends RuntimeException {
    private final boolean configuration;

    public SpeechTranscriptionException(String message, boolean configuration) {
        super(message);
        this.configuration = configuration;
    }

    public SpeechTranscriptionException(String message, boolean configuration, Throwable cause) {
        super(message, cause);
        this.configuration = configuration;
    }

    public boolean isConfiguration() {
        return configuration;
    }
}
