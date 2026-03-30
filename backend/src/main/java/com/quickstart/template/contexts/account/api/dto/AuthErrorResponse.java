package com.quickstart.template.contexts.account.api.dto;

public class AuthErrorResponse {
    private String code;
    private String message;
    private boolean captchaRequired;
    private Integer remainingAttempts;

    public AuthErrorResponse() {}

    public AuthErrorResponse(String code, String message, boolean captchaRequired, Integer remainingAttempts) {
        this.code = code;
        this.message = message;
        this.captchaRequired = captchaRequired;
        this.remainingAttempts = remainingAttempts;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isCaptchaRequired() {
        return captchaRequired;
    }

    public void setCaptchaRequired(boolean captchaRequired) {
        this.captchaRequired = captchaRequired;
    }

    public Integer getRemainingAttempts() {
        return remainingAttempts;
    }

    public void setRemainingAttempts(Integer remainingAttempts) {
        this.remainingAttempts = remainingAttempts;
    }
}
