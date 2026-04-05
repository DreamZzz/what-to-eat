package com.quickstart.template.contexts.meal.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class MealIntentRequestDTO {
    @NotBlank(message = "sourceText is required")
    @Size(max = 120, message = "sourceText must be 120 characters or fewer")
    private String sourceText;

    @Size(max = 20, message = "locale must be 20 characters or fewer")
    private String locale = "zh-CN";

    public String getSourceText() {
        return sourceText;
    }

    public void setSourceText(String sourceText) {
        this.sourceText = sourceText;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }
}
