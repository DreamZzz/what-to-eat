package com.quickstart.template.contexts.meal.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quickstart.template.contexts.account.domain.User;
import com.quickstart.template.contexts.meal.api.dto.MealRecommendationFormDTO;
import com.quickstart.template.contexts.meal.api.dto.MealRecommendationRequestDTO;
import com.quickstart.template.contexts.meal.api.dto.MealRecommendationResponseDTO;
import com.quickstart.template.contexts.meal.api.dto.MealRecipeCollectionResponseDTO;
import com.quickstart.template.contexts.meal.api.dto.RecipeDTO;
import com.quickstart.template.contexts.meal.api.dto.RecipeIngredientDTO;
import com.quickstart.template.contexts.meal.api.dto.RecipeStepDTO;
import com.quickstart.template.contexts.meal.domain.MealCatalogItem;
import com.quickstart.template.contexts.meal.domain.MealRecipe;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class MealRecipeMapper {
    private final ObjectMapper objectMapper;

    public MealRecipeMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public MealRecommendationFormDTO toForm(MealRecommendationRequestDTO request) {
        MealRecommendationFormDTO form = new MealRecommendationFormDTO();
        form.setSourceMode(request.getSourceMode());
        form.setCatalogItemId(request.getCatalogItemId());
        form.setDishCount(request.getDishCount());
        form.setTotalCalories(request.getTotalCalories());
        form.setStaple(request.getStaple());
        form.setLocale(request.getLocale());
        return form;
    }

    public RecipeDTO toRecipeDTO(MealRecipe recipe) {
        RecipeDTO dto = new RecipeDTO();
        dto.setId(recipe.getId());
        dto.setCatalogItemId(recipe.getCatalogItem() != null ? recipe.getCatalogItem().getId() : null);
        dto.setTitle(recipe.getTitle());
        dto.setSummary(recipe.getSummary());
        dto.setEstimatedCalories(recipe.getEstimatedCalories());
        dto.setIngredients(readList(recipe.getIngredientsJson(), new TypeReference<List<RecipeIngredientDTO>>() {}));
        dto.setSeasonings(readList(recipe.getSeasoningsJson(), new TypeReference<List<RecipeIngredientDTO>>() {}));
        dto.setSteps(readList(recipe.getStepsJson(), new TypeReference<List<RecipeStepDTO>>() {}));
        dto.setImageUrl(recipe.getImageUrl());
        dto.setImageStatus(recipe.getImageStatus());
        dto.setStepsStatus(recipe.getStepsStatus());
        dto.setPreference(recipe.getPreference());
        return dto;
    }

    public MealRecipe toMealRecipe(
            User user,
            MealRecommendationRequestDTO request,
            MealCatalogItem catalogItem,
            String requestId,
            String provider,
            RecipeDTO recipeDTO) {
        MealRecipe recipe = new MealRecipe();
        recipe.setUser(user);
        recipe.setRequestId(requestId);
        recipe.setCatalogItem(catalogItem);
        recipe.setCatalogItemCode(catalogItem != null ? catalogItem.getCode() : null);
        recipe.setSourceText(request.getSourceText());
        recipe.setNormalizedSourceText(normalizeSourceText(request.getSourceText()));
        recipe.setSourceMode(request.getSourceMode());
        recipe.setDishCount(request.getDishCount());
        recipe.setTotalCalories(request.getTotalCalories());
        recipe.setStaple(request.getStaple());

        recipe.setLocale(request.getLocale());
        recipe.setProvider(provider);
        recipe.setTitle(recipeDTO.getTitle());
        recipe.setSummary(recipeDTO.getSummary());
        recipe.setEstimatedCalories(recipeDTO.getEstimatedCalories());
        recipe.setIngredientsJson(writeList(recipeDTO.getIngredients()));
        recipe.setSeasoningsJson(writeList(recipeDTO.getSeasonings()));
        recipe.setStepsJson(writeList(recipeDTO.getSteps()));
        recipe.setImageUrl(recipeDTO.getImageUrl());
        recipe.setImageStatus(recipeDTO.getImageStatus() == null ? "OMITTED" : recipeDTO.getImageStatus());
        recipe.setStepsStatus(recipeDTO.getStepsStatus() == null ? "OMITTED" : recipeDTO.getStepsStatus());
        recipe.setPreference(recipeDTO.getPreference());
        return recipe;
    }

    public MealRecipe copyMealRecipeForReuse(
            User user,
            MealRecommendationRequestDTO request,
            MealCatalogItem catalogItem,
            String requestId,
            MealRecipe sourceRecipe
    ) {
        MealRecipe recipe = new MealRecipe();
        recipe.setUser(user);
        recipe.setRequestId(requestId);
        recipe.setCatalogItem(catalogItem);
        recipe.setCatalogItemCode(catalogItem != null ? catalogItem.getCode() : null);
        recipe.setSourceText(request.getSourceText());
        recipe.setNormalizedSourceText(normalizeSourceText(request.getSourceText()));
        recipe.setSourceMode(request.getSourceMode());
        recipe.setDishCount(request.getDishCount());
        recipe.setTotalCalories(request.getTotalCalories());
        recipe.setStaple(request.getStaple());

        recipe.setLocale(request.getLocale());
        recipe.setProvider(sourceRecipe.getProvider());
        recipe.setTitle(sourceRecipe.getTitle());
        recipe.setSummary(sourceRecipe.getSummary());
        recipe.setEstimatedCalories(sourceRecipe.getEstimatedCalories());
        recipe.setIngredientsJson(sourceRecipe.getIngredientsJson());
        recipe.setSeasoningsJson(sourceRecipe.getSeasoningsJson());
        recipe.setStepsJson(sourceRecipe.getStepsJson());
        recipe.setImageUrl(sourceRecipe.getImageUrl());
        recipe.setImageStatus(sourceRecipe.getImageStatus());
        recipe.setStepsStatus(sourceRecipe.getStepsStatus() == null ? "OMITTED" : sourceRecipe.getStepsStatus());
        recipe.setPreference(null);
        return recipe;
    }

    public MealRecommendationResponseDTO toRecommendationResponse(
            String requestId,
            MealRecommendationRequestDTO request,
            String provider,
            List<RecipeDTO> recipes,
            boolean emptyState
    ) {
        MealRecommendationResponseDTO response = new MealRecommendationResponseDTO();
        response.setRequestId(requestId);
        response.setSourceText(request.getSourceText());
        response.setForm(toForm(request));
        response.setProvider(provider);
        response.setItems(recipes);
        response.setEmptyState(emptyState);
        return response;
    }

    public MealRecipeCollectionResponseDTO toCollectionResponse(List<MealRecipe> recipes, org.springframework.data.domain.Page<?> page, String scene, String provider) {
        List<RecipeDTO> items = toRecipeList(recipes);
        return MealRecipeCollectionResponseDTO.fromPage(items, page, scene, null, "latest", provider);
    }

    public List<RecipeDTO> toRecipeList(List<MealRecipe> recipes) {
        if (recipes == null || recipes.isEmpty()) {
            return Collections.emptyList();
        }
        return recipes.stream().map(this::toRecipeDTO).toList();
    }

    private <T> List<T> readList(String json, TypeReference<List<T>> typeReference) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }

        try {
            List<T> value = objectMapper.readValue(json, typeReference);
            return value == null ? new ArrayList<>() : value;
        } catch (Exception exception) {
            throw new MealGenerationException("Failed to read stored recipe payload", false, exception);
        }
    }

    private String writeList(Object value) {
        if (value == null) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new MealGenerationException("Failed to store recipe payload", true, exception);
        }
    }

    public String writeStepsJson(List<RecipeStepDTO> steps) {
        return writeList(steps);
    }

    static String normalizeSourceText(String sourceText) {
        if (sourceText == null) {
            return "";
        }
        return sourceText.trim().toLowerCase(java.util.Locale.ROOT);
    }

}
