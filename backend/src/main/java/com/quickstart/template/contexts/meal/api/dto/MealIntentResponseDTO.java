package com.quickstart.template.contexts.meal.api.dto;

public class MealIntentResponseDTO {
    private String decision;
    private String normalizedSourceText;
    private String clarificationQuestion;
    private Boolean catalogFirst;
    private Long catalogItemId;

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public String getNormalizedSourceText() {
        return normalizedSourceText;
    }

    public void setNormalizedSourceText(String normalizedSourceText) {
        this.normalizedSourceText = normalizedSourceText;
    }

    public String getClarificationQuestion() {
        return clarificationQuestion;
    }

    public void setClarificationQuestion(String clarificationQuestion) {
        this.clarificationQuestion = clarificationQuestion;
    }

    public Boolean getCatalogFirst() {
        return catalogFirst;
    }

    public void setCatalogFirst(Boolean catalogFirst) {
        this.catalogFirst = catalogFirst;
    }

    public Long getCatalogItemId() {
        return catalogItemId;
    }

    public void setCatalogItemId(Long catalogItemId) {
        this.catalogItemId = catalogItemId;
    }
}
