package com.quickstart.template.contexts.meal.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class RecipePreferenceRequestDTO {
    @NotBlank
    @Pattern(regexp = "^(LIKE|DISLIKE)$")
    private String preference;

    public String getPreference() {
        return preference;
    }

    public void setPreference(String preference) {
        this.preference = preference;
    }
}
