package com.quickstart.template.contexts.meal.application;

public class MealImageResult {
    private final String provider;
    private final String imageUrl;
    private final String imageStatus;

    public MealImageResult(String provider, String imageUrl, String imageStatus) {
        this.provider = provider;
        this.imageUrl = imageUrl;
        this.imageStatus = imageStatus;
    }

    public String getProvider() {
        return provider;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public String getImageStatus() {
        return imageStatus;
    }
}
