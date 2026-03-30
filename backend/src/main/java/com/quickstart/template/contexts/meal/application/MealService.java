package com.quickstart.template.contexts.meal.application;

import com.quickstart.template.contexts.account.domain.User;
import com.quickstart.template.contexts.account.infrastructure.persistence.UserRepository;
import com.quickstart.template.contexts.meal.api.dto.MealRecommendationRequestDTO;
import com.quickstart.template.contexts.meal.api.dto.MealRecommendationResponseDTO;
import com.quickstart.template.contexts.meal.api.dto.MealCatalogResponseDTO;
import com.quickstart.template.contexts.meal.api.dto.MealRecipeCollectionResponseDTO;
import com.quickstart.template.contexts.meal.api.dto.RecipeDTO;
import com.quickstart.template.contexts.meal.api.dto.RecipePreferenceResponseDTO;
import com.quickstart.template.contexts.meal.domain.MealCatalogItem;
import com.quickstart.template.contexts.meal.domain.MealRecipe;
import com.quickstart.template.contexts.meal.infrastructure.persistence.MealRecipeRepository;
import com.quickstart.template.platform.provider.recipeai.MealGenerationProvider;
import com.quickstart.template.platform.provider.recipeai.MealImageProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

@Service
public class MealService {
    private static final String PREFERENCE_LIKE = "LIKE";
    private static final Logger log = LoggerFactory.getLogger(MealService.class);

    private final MealRecipeRepository mealRecipeRepository;
    private final UserRepository userRepository;
    private final MealGenerationProvider mealGenerationProvider;
    private final MealImageProvider mealImageProvider;
    private final MealRecipeMapper mealRecipeMapper;
    private final MealCatalogService mealCatalogService;

    public MealService(
            MealRecipeRepository mealRecipeRepository,
            UserRepository userRepository,
            MealGenerationProvider mealGenerationProvider,
            MealImageProvider mealImageProvider,
            MealRecipeMapper mealRecipeMapper,
            MealCatalogService mealCatalogService) {
        this.mealRecipeRepository = mealRecipeRepository;
        this.userRepository = userRepository;
        this.mealGenerationProvider = mealGenerationProvider;
        this.mealImageProvider = mealImageProvider;
        this.mealRecipeMapper = mealRecipeMapper;
        this.mealCatalogService = mealCatalogService;
    }

    @Transactional
    public MealRecommendationResponseDTO recommendRecipes(MealRecommendationRequestDTO request, Long userId) {
        User user = resolveUser(userId);
        MealCatalogItem catalogItem = resolveCatalogItem(request.getCatalogItemId());
        Optional<MealRecommendationResponseDTO> cachedResponse = reuseExistingRecipes(request, user, catalogItem);
        if (cachedResponse.isPresent()) {
            return cachedResponse.get();
        }

        String requestId = UUID.randomUUID().toString();
        log.info("Meal recommendation request {} using textProvider={} imageProvider={}",
                requestId,
                mealGenerationProvider.providerName(),
                mealImageProvider.providerName());

        MealGenerationResult generationResult = mealGenerationProvider.generate(request);
        List<RecipeDTO> recipes = enrichRecipesWithImages(request, generationResult.getRecipes(), catalogItem);
        for (RecipeDTO recipe : recipes) {
            MealImageResult imageResult = new MealImageResult(
                    mealImageProvider.providerName(),
                    recipe.getImageUrl(),
                    recipe.getImageStatus()
            );
            recipe.setImageUrl(imageResult.getImageUrl());
            recipe.setImageStatus(imageResult.getImageStatus());
            recipe.setPreference(null);
            recipe.setCatalogItemId(catalogItem != null ? catalogItem.getId() : null);

            MealRecipe savedRecipe = mealRecipeMapper.toMealRecipe(
                    user,
                    request,
                    catalogItem,
                    requestId,
                    generationResult.getProvider(),
                    recipe
            );
            if (recipe.getImageUrl() == null) {
                savedRecipe.setImageStatus(imageResult.getImageStatus());
            }
            MealRecipe saved = mealRecipeRepository.save(savedRecipe);
            recipe.setId(saved.getId());
            recipe.setPreference(saved.getPreference());
            recipe.setImageStatus(saved.getImageStatus());
        }

        return mealRecipeMapper.toRecommendationResponse(
                requestId,
                request,
                generationResult.getProvider(),
                recipes,
                generationResult.isEmptyState() || recipes.isEmpty()
        );
    }

    private Optional<MealRecommendationResponseDTO> reuseExistingRecipes(
            MealRecommendationRequestDTO request,
            User user,
            MealCatalogItem catalogItem
    ) {
        Optional<String> reusableRequestId = mealRecipeRepository.findLatestReusableRequestId(
                normalizeRequiredText(request.getSourceText()),
                request.getDishCount(),
                request.getTotalCalories(),
                normalizeNullableText(request.getStaple()),
                normalizeNullableText(request.getFlavor()),
                normalizeNullableText(request.getLocale()),
                catalogItem != null ? catalogItem.getId() : null
        );

        if (reusableRequestId.isEmpty()) {
            return Optional.empty();
        }

        List<MealRecipe> cachedRecipes = mealRecipeRepository.findAllByRequestIdOrderByIdAsc(reusableRequestId.get());
        if (cachedRecipes.isEmpty()) {
            return Optional.empty();
        }

        Long cachedOwnerId = cachedRecipes.get(0).getUser() == null ? null : cachedRecipes.get(0).getUser().getId();
        if (Objects.equals(cachedOwnerId, user.getId())) {
            log.info("Meal recommendation cache hit requestId={} userId={}", reusableRequestId.get(), user.getId());
            return Optional.of(mealRecipeMapper.toRecommendationResponse(
                    reusableRequestId.get(),
                    request,
                    "database",
                    mealRecipeMapper.toRecipeList(cachedRecipes),
                    false
            ));
        }

        String clonedRequestId = UUID.randomUUID().toString();
        log.info(
                "Meal recommendation cache hit requestId={} reused for userId={} via clonedRequestId={}",
                reusableRequestId.get(),
                user.getId(),
                clonedRequestId
        );

        List<RecipeDTO> clonedRecipes = new ArrayList<>();
        for (MealRecipe cachedRecipe : cachedRecipes) {
            MealRecipe clone = mealRecipeMapper.copyMealRecipeForReuse(
                    user,
                    request,
                    catalogItem,
                    clonedRequestId,
                    cachedRecipe
            );
            MealRecipe savedClone = mealRecipeRepository.save(clone);
            clonedRecipes.add(mealRecipeMapper.toRecipeDTO(savedClone));
        }

        return Optional.of(mealRecipeMapper.toRecommendationResponse(
                clonedRequestId,
                request,
                "database",
                clonedRecipes,
                clonedRecipes.isEmpty()
        ));
    }

    private List<RecipeDTO> enrichRecipesWithImages(
            MealRecommendationRequestDTO request,
            List<RecipeDTO> recipes,
            MealCatalogItem catalogItem
    ) {
        if (recipes == null || recipes.isEmpty()) {
            return List.of();
        }

        IntStream indexStream = IntStream.range(0, recipes.size());
        if (recipes.size() > 1) {
            log.info("Meal recommendation image enrichment running in parallel for {} recipes", recipes.size());
            indexStream = indexStream.parallel();
        }

        return indexStream
                .mapToObj(index -> enrichRecipeWithImage(request, recipes.get(index), catalogItem))
                .toList();
    }

    private RecipeDTO enrichRecipeWithImage(
            MealRecommendationRequestDTO request,
            RecipeDTO recipe,
            MealCatalogItem catalogItem
    ) {
        MealImageResult imageResult = mealImageProvider.generate(request, recipe);
        recipe.setImageUrl(imageResult.getImageUrl());
        recipe.setImageStatus(imageResult.getImageStatus());
        recipe.setPreference(null);
        recipe.setCatalogItemId(catalogItem != null ? catalogItem.getId() : null);
        return recipe;
    }

    public MealCatalogResponseDTO getCatalog() {
        return mealCatalogService.getCatalog();
    }

    public Optional<RecipeDTO> getRecipe(Long recipeId, Long userId) {
        return mealRecipeRepository.findByIdAndUserId(recipeId, userId)
                .map(mealRecipeMapper::toRecipeDTO);
    }

    @Transactional
    public Optional<RecipePreferenceResponseDTO> updatePreference(Long recipeId, String preference, Long userId) {
        Optional<MealRecipe> recipeOptional = mealRecipeRepository.findByIdAndUserId(recipeId, userId);
        if (recipeOptional.isEmpty()) {
            return Optional.empty();
        }

        MealRecipe recipe = recipeOptional.get();
        recipe.setPreference(preference);
        recipe.setUpdatedAt(LocalDateTime.now());
        MealRecipe saved = mealRecipeRepository.save(recipe);

        RecipePreferenceResponseDTO response = new RecipePreferenceResponseDTO();
        response.setRecipeId(saved.getId());
        response.setPreference(saved.getPreference());
        response.setUpdatedAt(saved.getUpdatedAt() != null ? saved.getUpdatedAt() : recipe.getUpdatedAt());
        return Optional.of(response);
    }

    public MealRecipeCollectionResponseDTO getFavorites(Long userId, Pageable pageable) {
        Page<MealRecipe> page = mealRecipeRepository.findAllByUserIdAndPreferenceOrderByUpdatedAtDesc(
                userId,
                PREFERENCE_LIKE,
                pageable
        );
        return mealRecipeMapper.toCollectionResponse(
                page.getContent(),
                page,
                "favorites",
                "database"
        );
    }

    private User resolveUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new MealGenerationException("当前用户不存在", true));
    }

    private MealCatalogItem resolveCatalogItem(Long catalogItemId) {
        if (catalogItemId == null) {
            return null;
        }

        return mealCatalogService.findItemById(catalogItemId)
                .orElseThrow(() -> new IllegalArgumentException("基础菜单菜品不存在"));
    }

    private String normalizeRequiredText(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeNullableText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
