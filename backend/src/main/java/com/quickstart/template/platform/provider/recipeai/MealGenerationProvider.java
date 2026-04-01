package com.quickstart.template.platform.provider.recipeai;

import com.quickstart.template.contexts.meal.api.dto.MealRecommendationRequestDTO;
import com.quickstart.template.contexts.meal.api.dto.RecipeDTO;
import com.quickstart.template.contexts.meal.api.dto.RecipeStepDTO;
import com.quickstart.template.contexts.meal.application.MealGenerationResult;

import java.util.function.Consumer;

public interface MealGenerationProvider {
    String providerName();

    MealGenerationResult generate(MealRecommendationRequestDTO request);

    /**
     * Streaming variant: invoke {@code onRecipe} for each recipe as soon as it is ready,
     * instead of waiting for the full batch. The default implementation falls back to
     * {@link #generate} and emits all results at once.
     */
    default void generateStream(MealRecommendationRequestDTO request, Consumer<RecipeDTO> onRecipe) {
        generate(request).getRecipes().forEach(onRecipe);
    }

    /**
     * Phase-2 steps generation: stream the cooking steps for a single recipe via LLM SSE,
     * invoking {@code onStep} for each step object as soon as it is fully parsed.
     * The default implementation emits any steps already present on the recipe.
     *
     * @param recipe recipe card (must have title; ingredients/seasonings improve quality)
     * @param locale BCP-47 locale for the response language (e.g. "zh-CN")
     * @param onStep called for each completed step in order
     */
    default void streamRecipeSteps(RecipeDTO recipe, String locale, Consumer<RecipeStepDTO> onStep) {
        if (recipe.getSteps() != null) {
            recipe.getSteps().forEach(onStep);
        }
    }
}
