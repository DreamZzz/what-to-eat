package com.quickstart.template.contexts.meal.api.dto;

public class RecipeImageResponseDTO {
    private Long recipeId;
    private String imageUrl;
    private String imageStatus;

    public RecipeImageResponseDTO(Long recipeId, String imageUrl, String imageStatus) {
        this.recipeId = recipeId;
        this.imageUrl = imageUrl;
        this.imageStatus = imageStatus;
    }

    public Long getRecipeId() { return recipeId; }
    public String getImageUrl() { return imageUrl; }
    public String getImageStatus() { return imageStatus; }
}
