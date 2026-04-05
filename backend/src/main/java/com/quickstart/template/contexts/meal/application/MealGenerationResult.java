package com.quickstart.template.contexts.meal.application;

import com.quickstart.template.contexts.meal.api.dto.RecipeDTO;

import java.util.List;

public class MealGenerationResult {
    private final String provider;
    private final List<RecipeDTO> recipes;
    private final boolean emptyState;
    private final String reasonSummary;

    public MealGenerationResult(String provider, List<RecipeDTO> recipes, boolean emptyState) {
        this(provider, recipes, emptyState, null);
    }

    public MealGenerationResult(String provider, List<RecipeDTO> recipes, boolean emptyState, String reasonSummary) {
        this.provider = provider;
        this.recipes = recipes;
        this.emptyState = emptyState;
        this.reasonSummary = reasonSummary;
    }

    public String getProvider() {
        return provider;
    }

    public List<RecipeDTO> getRecipes() {
        return recipes;
    }

    public boolean isEmptyState() {
        return emptyState;
    }

    public String getReasonSummary() {
        return reasonSummary;
    }
}
