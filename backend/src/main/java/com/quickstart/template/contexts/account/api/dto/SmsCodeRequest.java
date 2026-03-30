package com.quickstart.template.contexts.account.api.dto;

import jakarta.validation.constraints.NotBlank;

public class SmsCodeRequest {
    @NotBlank
    private String phone;

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }
}
