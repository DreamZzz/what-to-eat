package com.quickstart.template.contexts.meal.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Pattern;

import java.util.List;

public class MealRecommendationRequestDTO {
    @NotBlank
    private String sourceText;

    @NotBlank
    @Pattern(regexp = "^(TEXT|VOICE)$")
    private String sourceMode;

    @Positive
    private Long catalogItemId;

    @NotNull
    @Min(1)
    @Max(6)
    private Integer dishCount;

    @NotNull
    @Min(1)
    @Max(5000)
    private Integer totalCalories;

    @NotBlank
    @Pattern(regexp = "^(RICE|NOODLES|COARSE_GRAINS|NO_STAPLE)$")
    private String staple;

    private String locale;

    /**
     * Server-side only: titles of dishes recently recommended to this user.
     * Populated by {@code MealService} before calling the generation provider.
     * Not deserialized from the HTTP request body.
     */
    @JsonIgnore
    private List<String> recentDishTitles;

    public String getSourceText() {
        return sourceText;
    }

    public void setSourceText(String sourceText) {
        this.sourceText = sourceText;
    }

    public String getSourceMode() {
        return sourceMode;
    }

    public void setSourceMode(String sourceMode) {
        this.sourceMode = sourceMode;
    }

    public Long getCatalogItemId() {
        return catalogItemId;
    }

    public void setCatalogItemId(Long catalogItemId) {
        this.catalogItemId = catalogItemId;
    }

    public Integer getDishCount() {
        return dishCount;
    }

    public void setDishCount(Integer dishCount) {
        this.dishCount = dishCount;
    }

    public Integer getTotalCalories() {
        return totalCalories;
    }

    public void setTotalCalories(Integer totalCalories) {
        this.totalCalories = totalCalories;
    }

    public String getStaple() {
        return staple;
    }

    public void setStaple(String staple) {
        this.staple = staple;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    public List<String> getRecentDishTitles() {
        return recentDishTitles;
    }

    public void setRecentDishTitles(List<String> recentDishTitles) {
        this.recentDishTitles = recentDishTitles;
    }
}
