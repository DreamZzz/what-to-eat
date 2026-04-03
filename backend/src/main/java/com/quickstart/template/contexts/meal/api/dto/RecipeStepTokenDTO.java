package com.quickstart.template.contexts.meal.api.dto;

public class RecipeStepTokenDTO {
    private Integer index;
    private String contentDelta;

    public Integer getIndex() {
        return index;
    }

    public void setIndex(Integer index) {
        this.index = index;
    }

    public String getContentDelta() {
        return contentDelta;
    }

    public void setContentDelta(String contentDelta) {
        this.contentDelta = contentDelta;
    }
}
