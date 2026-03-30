package com.quickstart.template.contexts.meal.api.dto;

public class MealRecommendationFormDTO {
    private String sourceMode;
    private Long catalogItemId;
    private Integer dishCount;
    private Integer totalCalories;
    private String staple;
    private String flavor;
    private String locale;

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

    public String getFlavor() {
        return flavor;
    }

    public void setFlavor(String flavor) {
        this.flavor = flavor;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }
}
