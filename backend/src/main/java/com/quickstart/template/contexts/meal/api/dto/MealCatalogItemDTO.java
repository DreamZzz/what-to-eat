package com.quickstart.template.contexts.meal.api.dto;

import java.util.List;

public class MealCatalogItemDTO {
    private Long id;
    private String code;
    private String slug;
    private String name;
    private String category;
    private String subcategory;
    private String cookingMethod;
    private String rawFlavorText;
    private List<String> flavorTags;
    private List<String> featureTags;
    private List<String> ingredientTags;
    private Integer sourceIndex;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getSubcategory() {
        return subcategory;
    }

    public void setSubcategory(String subcategory) {
        this.subcategory = subcategory;
    }

    public String getCookingMethod() {
        return cookingMethod;
    }

    public void setCookingMethod(String cookingMethod) {
        this.cookingMethod = cookingMethod;
    }

    public String getRawFlavorText() {
        return rawFlavorText;
    }

    public void setRawFlavorText(String rawFlavorText) {
        this.rawFlavorText = rawFlavorText;
    }

    public List<String> getFlavorTags() {
        return flavorTags;
    }

    public void setFlavorTags(List<String> flavorTags) {
        this.flavorTags = flavorTags;
    }

    public List<String> getFeatureTags() {
        return featureTags;
    }

    public void setFeatureTags(List<String> featureTags) {
        this.featureTags = featureTags;
    }

    public List<String> getIngredientTags() {
        return ingredientTags;
    }

    public void setIngredientTags(List<String> ingredientTags) {
        this.ingredientTags = ingredientTags;
    }

    public Integer getSourceIndex() {
        return sourceIndex;
    }

    public void setSourceIndex(Integer sourceIndex) {
        this.sourceIndex = sourceIndex;
    }
}
