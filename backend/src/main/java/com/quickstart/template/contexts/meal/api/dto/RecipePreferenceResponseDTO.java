package com.quickstart.template.contexts.meal.api.dto;

import java.time.LocalDateTime;

public class RecipePreferenceResponseDTO {
    private Long recipeId;
    private String preference;
    private LocalDateTime updatedAt;

    public Long getRecipeId() {
        return recipeId;
    }

    public void setRecipeId(Long recipeId) {
        this.recipeId = recipeId;
    }

    public String getPreference() {
        return preference;
    }

    public void setPreference(String preference) {
        this.preference = preference;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
