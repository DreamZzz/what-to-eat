package com.quickstart.template.platform.provider.sms;

public class SmsDeliveryException extends RuntimeException {
    public enum FailureType {
        CONFIGURATION,
        DELIVERY
    }

    private final FailureType failureType;
    private final String providerCode;
    private final String providerMessage;

    public SmsDeliveryException(FailureType failureType, String message) {
        this(failureType, message, null, null, null);
    }

    public SmsDeliveryException(FailureType failureType, String message, Throwable cause) {
        this(failureType, message, null, null, cause);
    }

    public SmsDeliveryException(FailureType failureType, String message, String providerCode, String providerMessage) {
        this(failureType, message, providerCode, providerMessage, null);
    }

    public SmsDeliveryException(
            FailureType failureType,
            String message,
            String providerCode,
            String providerMessage,
            Throwable cause) {
        super(message, cause);
        this.failureType = failureType;
        this.providerCode = providerCode;
        this.providerMessage = providerMessage;
    }

    public FailureType getFailureType() {
        return failureType;
    }

    public String getProviderCode() {
        return providerCode;
    }

    public String getProviderMessage() {
        return providerMessage;
    }
}
