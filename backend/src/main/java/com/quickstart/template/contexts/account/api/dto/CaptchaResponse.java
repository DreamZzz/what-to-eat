package com.quickstart.template.contexts.account.api.dto;

public class CaptchaResponse {
    private String captchaId;
    private String imageBase64;
    private long expiresInSeconds;

    public CaptchaResponse() {}

    public CaptchaResponse(String captchaId, String imageBase64, long expiresInSeconds) {
        this.captchaId = captchaId;
        this.imageBase64 = imageBase64;
        this.expiresInSeconds = expiresInSeconds;
    }

    public String getCaptchaId() {
        return captchaId;
    }

    public void setCaptchaId(String captchaId) {
        this.captchaId = captchaId;
    }

    public String getImageBase64() {
        return imageBase64;
    }

    public void setImageBase64(String imageBase64) {
        this.imageBase64 = imageBase64;
    }

    public long getExpiresInSeconds() {
        return expiresInSeconds;
    }

    public void setExpiresInSeconds(long expiresInSeconds) {
        this.expiresInSeconds = expiresInSeconds;
    }
}
