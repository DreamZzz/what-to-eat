package com.quickstart.template.platform.provider.mail;

public class EmailDeliveryException extends RuntimeException {
    public enum FailureType {
        CONFIGURATION,
        DELIVERY
    }

    private final FailureType failureType;

    public EmailDeliveryException(FailureType failureType, String message) {
        this(failureType, message, null);
    }

    public EmailDeliveryException(FailureType failureType, String message, Throwable cause) {
        super(message, cause);
        this.failureType = failureType;
    }

    public FailureType getFailureType() {
        return failureType;
    }
}
