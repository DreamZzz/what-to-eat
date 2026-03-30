package com.quickstart.template.contexts.meal.api.dto;

import java.util.List;

public class MealRecommendationResponseDTO {
    private String requestId;
    private String sourceText;
    private MealRecommendationFormDTO form;
    private String provider;
    private List<RecipeDTO> items;
    private Boolean emptyState;

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getSourceText() {
        return sourceText;
    }

    public void setSourceText(String sourceText) {
        this.sourceText = sourceText;
    }

    public MealRecommendationFormDTO getForm() {
        return form;
    }

    public void setForm(MealRecommendationFormDTO form) {
        this.form = form;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public List<RecipeDTO> getItems() {
        return items;
    }

    public void setItems(List<RecipeDTO> items) {
        this.items = items;
    }

    public Boolean getEmptyState() {
        return emptyState;
    }

    public void setEmptyState(Boolean emptyState) {
        this.emptyState = emptyState;
    }
}
