package com.quickstart.template.platform.provider.recipeai;

import com.quickstart.template.contexts.meal.api.dto.MealRecommendationRequestDTO;
import com.quickstart.template.contexts.meal.api.dto.RecipeDTO;
import com.quickstart.template.contexts.meal.api.dto.RecipeIngredientDTO;
import com.quickstart.template.contexts.meal.api.dto.RecipeStepDTO;
import com.quickstart.template.contexts.meal.application.MealGenerationResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
@ConditionalOnProperty(name = "app.meal.provider", havingValue = "mock", matchIfMissing = true)
public class MockMealGenerationProvider implements MealGenerationProvider {

    @Override
    public String providerName() {
        return "mock";
    }

    @Override
    public MealGenerationResult generate(MealRecommendationRequestDTO request) {
        int count = request.getDishCount() == null ? 1 : request.getDishCount();
        List<RecipeDTO> recipes = new ArrayList<>();
        String focus = inferFocus(request.getSourceText());
        int baseCalories = Math.max(180, request.getTotalCalories() / Math.max(count, 1));

        for (int i = 0; i < count; i++) {
            RecipeDTO recipe = new RecipeDTO();
            recipe.setTitle(buildTitle(focus, request, i));
            recipe.setSummary(buildSummary(request, i));
            recipe.setEstimatedCalories(baseCalories + (i * 18));
            recipe.setIngredients(buildIngredients(focus, i));
            recipe.setSeasonings(buildSeasonings(i));
            recipe.setSteps(buildSteps(recipe.getTitle(), i));
            recipe.setImageStatus("OMITTED");
            recipes.add(recipe);
        }

        return new MealGenerationResult(providerName(), recipes, recipes.isEmpty());
    }

    private String inferFocus(String sourceText) {
        if (sourceText == null) {
            return "家常";
        }
        String text = sourceText.toLowerCase(Locale.ROOT);
        if (text.contains("面")) {
            return "面食";
        }
        if (text.contains("米饭") || text.contains("饭")) {
            return "米饭";
        }
        if (text.contains("汤")) {
            return "汤品";
        }
        if (text.contains("肉")) {
            return "荤菜";
        }
        return "家常";
    }

    private String buildTitle(String focus, MealRecommendationRequestDTO request, int index) {
        String[] suffixes = new String[] {"轻盈版", "元气版", "下饭版", "暖胃版", "清爽版", "活力版"};
        String suffix = suffixes[index % suffixes.length];
        return focus + describeFlavor(request.getFlavor()) + suffix;
    }

    private String buildSummary(MealRecommendationRequestDTO request, int index) {
        return String.format(
                Locale.ROOT,
                "根据%s、%d菜和%d千卡生成的第%d道推荐，适合%s口味。",
                request.getSourceText(),
                request.getDishCount(),
                request.getTotalCalories(),
                index + 1,
                describeFlavor(request.getFlavor())
        );
    }

    private List<RecipeIngredientDTO> buildIngredients(String focus, int index) {
        List<RecipeIngredientDTO> ingredients = new ArrayList<>();
        ingredients.add(createIngredient(focus + "主料", "120g"));
        ingredients.add(createIngredient("时蔬", "80g"));
        ingredients.add(createIngredient("鸡蛋", String.valueOf(index % 2 == 0 ? 1 : 2) + "个"));
        return ingredients;
    }

    private List<RecipeIngredientDTO> buildSeasonings(int index) {
        List<RecipeIngredientDTO> seasonings = new ArrayList<>();
        seasonings.add(createIngredient("生抽", "1勺"));
        seasonings.add(createIngredient("盐", "适量"));
        seasonings.add(createIngredient("葱花", index % 2 == 0 ? "少许" : "1把"));
        return seasonings;
    }

    private List<RecipeStepDTO> buildSteps(String title, int index) {
        List<RecipeStepDTO> steps = new ArrayList<>();
        steps.add(createStep(1, "准备" + title + "所需食材并完成清洗。"));
        steps.add(createStep(2, "根据口味控制火候，先处理主料再加入辅料。"));
        steps.add(createStep(3, "出锅前调味，保持" + (index % 2 == 0 ? "清爽" : "浓郁") + "口感。"));
        return steps;
    }

    private RecipeIngredientDTO createIngredient(String name, String amount) {
        RecipeIngredientDTO ingredient = new RecipeIngredientDTO();
        ingredient.setName(name);
        ingredient.setAmount(amount);
        return ingredient;
    }

    private RecipeStepDTO createStep(int index, String content) {
        RecipeStepDTO step = new RecipeStepDTO();
        step.setIndex(index);
        step.setContent(content);
        return step;
    }

    private String describeFlavor(String flavor) {
        if (flavor == null) {
            return "清淡";
        }
        return switch (flavor.toUpperCase(Locale.ROOT)) {
            case "APPETIZING" -> "开胃";
            case "RICH" -> "开荤";
            default -> "清淡";
        };
    }
}
