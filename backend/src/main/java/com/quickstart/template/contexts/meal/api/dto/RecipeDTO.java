package com.quickstart.template.contexts.meal.api.dto;

import java.util.List;

public class RecipeDTO {
    private Long id;
    private Long catalogItemId;
    private String title;
    private String summary;
    private Integer estimatedCalories;
    private List<RecipeIngredientDTO> ingredients;
    private List<RecipeIngredientDTO> seasonings;
    private List<RecipeStepDTO> steps;
    private String imageUrl;
    private String imageStatus;
    private String stepsStatus;
    private String preference;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCatalogItemId() {
        return catalogItemId;
    }

    public void setCatalogItemId(Long catalogItemId) {
        this.catalogItemId = catalogItemId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public Integer getEstimatedCalories() {
        return estimatedCalories;
    }

    public void setEstimatedCalories(Integer estimatedCalories) {
        this.estimatedCalories = estimatedCalories;
    }

    public List<RecipeIngredientDTO> getIngredients() {
        return ingredients;
    }

    public void setIngredients(List<RecipeIngredientDTO> ingredients) {
        this.ingredients = ingredients;
    }

    public List<RecipeIngredientDTO> getSeasonings() {
        return seasonings;
    }

    public void setSeasonings(List<RecipeIngredientDTO> seasonings) {
        this.seasonings = seasonings;
    }

    public List<RecipeStepDTO> getSteps() {
        return steps;
    }

    public void setSteps(List<RecipeStepDTO> steps) {
        this.steps = steps;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getImageStatus() {
        return imageStatus;
    }

    public void setImageStatus(String imageStatus) {
        this.imageStatus = imageStatus;
    }

    public String getStepsStatus() {
        return stepsStatus;
    }

    public void setStepsStatus(String stepsStatus) {
        this.stepsStatus = stepsStatus;
    }

    public String getPreference() {
        return preference;
    }

    public void setPreference(String preference) {
        this.preference = preference;
    }
}
