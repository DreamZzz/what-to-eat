package com.quickstart.template.platform.provider.recipeai;

import com.quickstart.template.contexts.meal.api.dto.MealRecommendationRequestDTO;
import com.quickstart.template.contexts.meal.api.dto.RecipeDTO;
import com.quickstart.template.contexts.meal.application.MealImageResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.meal.image.provider", havingValue = "disabled", matchIfMissing = true)
public class DisabledMealImageProvider implements MealImageProvider {

    @Override
    public String providerName() {
        return "disabled";
    }

    @Override
    public MealImageResult generate(MealRecommendationRequestDTO request, RecipeDTO recipe) {
        return new MealImageResult(providerName(), null, "OMITTED");
    }
}
