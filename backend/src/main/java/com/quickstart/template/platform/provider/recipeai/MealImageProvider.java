package com.quickstart.template.platform.provider.recipeai;

import com.quickstart.template.contexts.meal.api.dto.MealRecommendationRequestDTO;
import com.quickstart.template.contexts.meal.api.dto.RecipeDTO;
import com.quickstart.template.contexts.meal.application.MealImageResult;

public interface MealImageProvider {
    String providerName();

    MealImageResult generate(MealRecommendationRequestDTO request, RecipeDTO recipe);
}
