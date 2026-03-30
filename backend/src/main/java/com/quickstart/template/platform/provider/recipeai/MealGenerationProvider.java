package com.quickstart.template.platform.provider.recipeai;

import com.quickstart.template.contexts.meal.api.dto.MealRecommendationRequestDTO;
import com.quickstart.template.contexts.meal.application.MealGenerationResult;

public interface MealGenerationProvider {
    String providerName();

    MealGenerationResult generate(MealRecommendationRequestDTO request);
}
