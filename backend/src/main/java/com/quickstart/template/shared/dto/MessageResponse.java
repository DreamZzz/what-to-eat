package com.quickstart.template.shared.dto;

public class MessageResponse {
    private String message;
    private String service;
    private String provider;
    private Boolean setupRequired;

    public MessageResponse() {}

    public MessageResponse(String message) {
        this.message = message;
    }

    public MessageResponse(String message, String service, String provider, Boolean setupRequired) {
        this.message = message;
        this.service = service;
        this.provider = provider;
        this.setupRequired = setupRequired;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public Boolean getSetupRequired() {
        return setupRequired;
    }

    public void setSetupRequired(Boolean setupRequired) {
        this.setupRequired = setupRequired;
    }
}
