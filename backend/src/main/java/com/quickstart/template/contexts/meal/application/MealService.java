package com.quickstart.template.contexts.meal.application;

import com.quickstart.template.contexts.account.domain.User;
import com.quickstart.template.contexts.account.infrastructure.persistence.UserRepository;
import com.quickstart.template.contexts.meal.api.dto.MealRecommendationRequestDTO;
import com.quickstart.template.contexts.meal.api.dto.MealRecommendationResponseDTO;
import com.quickstart.template.contexts.meal.api.dto.MealCatalogResponseDTO;
import com.quickstart.template.contexts.meal.api.dto.MealIntentResponseDTO;
import com.quickstart.template.contexts.meal.api.dto.MealRecipeCollectionResponseDTO;
import com.quickstart.template.contexts.meal.api.dto.RecipeDTO;
import com.quickstart.template.contexts.meal.api.dto.RecipeImageResponseDTO;
import com.quickstart.template.contexts.meal.api.dto.RecipePreferenceResponseDTO;
import com.quickstart.template.contexts.meal.api.dto.RecipeStepDTO;
import com.quickstart.template.contexts.meal.api.dto.RecipeStepTokenDTO;
import com.quickstart.template.contexts.meal.domain.MealCatalogItem;
import com.quickstart.template.contexts.meal.domain.MealRecipe;
import com.quickstart.template.contexts.meal.infrastructure.persistence.MealRecipeRepository;
import com.quickstart.template.platform.provider.recipeai.MealGenerationProvider;
import com.quickstart.template.platform.provider.recipeai.MealImageProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

@Service
public class MealService {
    private static final String PREFERENCE_LIKE = "LIKE";
    private static final Logger log = LoggerFactory.getLogger(MealService.class);

    /** 不参与食材去重的基础调味料（这些几乎每道菜都有，排除在外避免误判）。 */
    private static final Set<String> EXCLUDED_FROM_DEDUP = Set.of(
            "盐", "食用盐", "食用油", "植物油", "大豆油", "菜籽油", "花生油", "橄榄油",
            "糖", "白糖", "红糖", "冰糖",
            "淀粉", "水淀粉", "生粉",
            "清水", "水"
    );

    /** 常见同义食材映射，归一化后再做去重比较。 */
    private static final Map<String, String> INGREDIENT_SYNONYMS = Map.of(
            "番茄", "西红柿",
            "土豆", "马铃薯",
            "菠萝", "凤梨",
            "香菇", "冬菇"
    );

    private final MealRecipeRepository mealRecipeRepository;
    private final UserRepository userRepository;
    private final MealGenerationProvider mealGenerationProvider;
    private final MealImageProvider mealImageProvider;
    private final MealRecipeMapper mealRecipeMapper;
    private final MealCatalogService mealCatalogService;
    private final MealIntentService mealIntentService;
    private final TransactionTemplate transactionTemplate;

    public MealService(
            MealRecipeRepository mealRecipeRepository,
            UserRepository userRepository,
            MealGenerationProvider mealGenerationProvider,
            MealImageProvider mealImageProvider,
            MealRecipeMapper mealRecipeMapper,
            MealCatalogService mealCatalogService,
            MealIntentService mealIntentService,
            PlatformTransactionManager transactionManager) {
        this.mealRecipeRepository = mealRecipeRepository;
        this.userRepository = userRepository;
        this.mealGenerationProvider = mealGenerationProvider;
        this.mealImageProvider = mealImageProvider;
        this.mealRecipeMapper = mealRecipeMapper;
        this.mealCatalogService = mealCatalogService;
        this.mealIntentService = mealIntentService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Transactional
    public MealRecommendationResponseDTO recommendRecipes(MealRecommendationRequestDTO request, Long userId) {
        applyIntentNormalization(request);
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

        injectRecentDishHistory(request, userId);
        MealGenerationResult generationResult = mealGenerationProvider.generate(request);

        // Images are fetched asynchronously by the client after the main response is
        // returned. Mark all recipes as PENDING so the frontend knows to request them.
        List<RecipeDTO> recipes = applyDishConstraints(generationResult.getRecipes()).stream()
                .map(recipe -> {
                    recipe.setImageStatus("PENDING");
                    recipe.setImageUrl(null);
                    recipe.setPreference(null);
                    recipe.setCatalogItemId(catalogItem != null ? catalogItem.getId() : null);
                    return recipe;
                })
                .toList();

        List<MealRecipe> entitiesToSave = recipes.stream()
                .map(recipe -> mealRecipeMapper.toMealRecipe(
                        user, request, catalogItem, requestId, generationResult.getProvider(), recipe))
                .toList();
        List<MealRecipe> savedEntities = mealRecipeRepository.saveAll(entitiesToSave);

        for (int i = 0; i < recipes.size(); i++) {
            RecipeDTO recipe = recipes.get(i);
            MealRecipe saved = savedEntities.get(i);
            recipe.setId(saved.getId());
            recipe.setPreference(saved.getPreference());
            recipe.setImageStatus(saved.getImageStatus());
        }

        return mealRecipeMapper.toRecommendationResponse(
                requestId,
                request,
                generationResult.getProvider(),
                generationResult.getReasonSummary(),
                recipes,
                generationResult.isEmptyState() || recipes.isEmpty()
        );
    }

    /**
     * Fetch and persist the image for a single recipe. Called by the frontend
     * individually per recipe after the main recommendation response is displayed.
     * If the image is already resolved (not PENDING) the stored result is returned
     * immediately without hitting the image provider again.
     */
    @Transactional
    public Optional<RecipeImageResponseDTO> fetchRecipeImage(Long recipeId, Long userId) {
        Optional<MealRecipe> recipeOpt = mealRecipeRepository.findByIdAndUserId(recipeId, userId);
        if (recipeOpt.isEmpty()) {
            return Optional.empty();
        }

        MealRecipe entity = recipeOpt.get();

        if (!"PENDING".equals(entity.getImageStatus())) {
            log.info("Recipe image already resolved recipeId={} status={}", recipeId, entity.getImageStatus());
            return Optional.of(new RecipeImageResponseDTO(entity.getId(), entity.getImageUrl(), entity.getImageStatus()));
        }

        RecipeDTO recipeDTO = mealRecipeMapper.toRecipeDTO(entity);
        // Passing null for request is intentional: the image provider resolves the dish
        // name from recipe.title first and falls back to request.sourceText.
        MealImageResult imageResult = mealImageProvider.generate(null, recipeDTO);

        entity.setImageUrl(imageResult.getImageUrl());
        entity.setImageStatus(imageResult.getImageStatus());
        entity.setUpdatedAt(LocalDateTime.now());
        mealRecipeRepository.save(entity);

        log.info("Recipe image fetched recipeId={} status={}", recipeId, imageResult.getImageStatus());
        return Optional.of(new RecipeImageResponseDTO(entity.getId(), entity.getImageUrl(), entity.getImageStatus()));
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

        if (request.getDishCount() != null
                && request.getDishCount() > 0
                && cachedRecipes.size() < request.getDishCount()) {
            log.warn(
                    "Meal recommendation cache skipped for requestId={} because cached recipe count {} is below requested {}",
                    reusableRequestId.get(),
                    cachedRecipes.size(),
                    request.getDishCount()
            );
            return Optional.empty();
        }

        if (!cachedRecipes.stream()
                .map(MealRecipe::getTitle)
                .allMatch(title -> mealCatalogService.resolveCatalogTitle(title).isPresent())) {
            log.warn("Meal recommendation cache skipped for requestId={} because cached titles are not valid catalog dishes",
                    reusableRequestId.get());
            return Optional.empty();
        }

        Long cachedOwnerId = cachedRecipes.get(0).getUser() == null ? null : cachedRecipes.get(0).getUser().getId();
        if (Objects.equals(cachedOwnerId, user.getId())) {
            log.info("Meal recommendation cache hit requestId={} userId={}", reusableRequestId.get(), user.getId());
            return Optional.of(mealRecipeMapper.toRecommendationResponse(
                    reusableRequestId.get(),
                    request,
                    "database",
                    buildFallbackReasonSummary(request, mealRecipeMapper.toRecipeList(cachedRecipes)),
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

        List<MealRecipe> clones = cachedRecipes.stream()
                .map(cachedRecipe -> mealRecipeMapper.copyMealRecipeForReuse(
                        user, request, catalogItem, clonedRequestId, cachedRecipe))
                .toList();
        List<MealRecipe> savedClones = mealRecipeRepository.saveAll(clones);
        List<RecipeDTO> clonedRecipes = savedClones.stream()
                .map(mealRecipeMapper::toRecipeDTO)
                .toList();

        return Optional.of(mealRecipeMapper.toRecommendationResponse(
                clonedRequestId,
                request,
                "database",
                buildFallbackReasonSummary(request, clonedRecipes),
                clonedRecipes,
                clonedRecipes.isEmpty()
        ));
    }

    /**
     * Streaming variant: emits each recipe via {@code onRecipe} as soon as it is saved.
     * Multi-dish requests run per-dish calls in parallel (see
     * {@link com.quickstart.template.platform.provider.recipeai.MealGenerationProvider#generateStream}).
     * Cache hits are handled atomically; fresh generation saves each recipe in its own transaction.
     */
    public void streamRecommendations(
            MealRecommendationRequestDTO request,
            Long userId,
            Consumer<String> onSummary,
            Consumer<RecipeDTO> onRecipe
    ) {
        applyIntentNormalization(request);
        // Phase 1: check cache (needs a transaction for potential clone saveAll)
        MealRecommendationResponseDTO cachedResponse = transactionTemplate.execute(status -> {
            User user = resolveUser(userId);
            MealCatalogItem catalogItem = resolveCatalogItem(request.getCatalogItemId());
            return reuseExistingRecipes(request, user, catalogItem).orElse(null);
        });

        if (cachedResponse != null) {
            if (cachedResponse.getReasonSummary() != null && !cachedResponse.getReasonSummary().isBlank()) {
                onSummary.accept(cachedResponse.getReasonSummary());
            }
            cachedResponse.getItems().forEach(onRecipe);
            return;
        }

        // Phase 2: generate fresh — each recipe saved in its own transaction so the
        // caller gets it immediately rather than waiting for the full batch.
        final String requestId = UUID.randomUUID().toString();
        final String providerName = mealGenerationProvider.providerName();
        final Long catalogItemId = request.getCatalogItemId();
        log.info("Stream recommendation request {} provider={}", requestId, providerName);

        injectRecentDishHistory(request, userId);

        int maxDishes = request.getDishCount() != null ? request.getDishCount() : 1;
        mealGenerationProvider.generateStream(
                request,
                onSummary,
                constrainedStreamConsumer(catalogItemId, maxDishes, recipe -> {
                    recipe.setImageStatus("PENDING");
                    recipe.setImageUrl(null);
                    recipe.setPreference(null);
                    recipe.setCatalogItemId(catalogItemId);

                    transactionTemplate.executeWithoutResult(status -> {
                        User txUser = userRepository.getReferenceById(userId);
                        MealCatalogItem txCatalogItem = catalogItemId != null
                                ? mealCatalogService.findItemById(catalogItemId).orElse(null)
                                : null;
                        MealRecipe entity = mealRecipeMapper.toMealRecipe(
                                txUser, request, txCatalogItem, requestId, providerName, recipe);
                        MealRecipe saved = mealRecipeRepository.save(entity);
                        recipe.setId(saved.getId());
                        recipe.setImageStatus(saved.getImageStatus());
                    });

                    onRecipe.accept(recipe);
                })
        );
    }

    /**
     * Phase-2 steps streaming: loads a saved recipe, generates its cooking steps via LLM
     * SSE, calls {@code onStep} for each step as it arrives, then persists the steps to DB.
     *
     * <p>If the recipe already has steps ({@code stepsStatus != "PENDING"}), emits the
     * stored steps immediately without a new LLM call.
     */
    public void streamRecipeSteps(
            Long recipeId,
            Long userId,
            String locale,
            Consumer<RecipeStepTokenDTO> onToken,
            Consumer<RecipeStepDTO> onStep
    ) {
        streamRecipeSteps(recipeId, userId, locale, onToken, onStep, () -> { });
    }

    public void streamRecipeSteps(
            Long recipeId,
            Long userId,
            String locale,
            Consumer<RecipeStepTokenDTO> onToken,
            Consumer<RecipeStepDTO> onStep,
            Runnable onStreamComplete
    ) {
        // Load the recipe (needs a transaction)
        MealRecipe entity = transactionTemplate.execute(status ->
                mealRecipeRepository.findByIdAndUserId(recipeId, userId).orElse(null));

        if (entity == null) {
            throw new IllegalArgumentException("菜谱不存在或无权访问");
        }

        // If steps are already present, emit them without calling the LLM
        if (!"PENDING".equals(entity.getStepsStatus())) {
            RecipeDTO existing = mealRecipeMapper.toRecipeDTO(entity);
            if (existing.getSteps() != null) {
                existing.getSteps().forEach(onStep);
            }
            onStreamComplete.run();
            return;
        }

        // Generate steps via LLM streaming (outside any transaction)
        RecipeDTO recipeCard = mealRecipeMapper.toRecipeDTO(entity);
        List<RecipeStepDTO> allSteps = new ArrayList<>();

        mealGenerationProvider.streamRecipeSteps(recipeCard, locale, onToken, step -> {
            allSteps.add(step);
            onStep.accept(step);
        });
        onStreamComplete.run();

        // Persist steps after successful streaming (new transaction)
        if (!allSteps.isEmpty()) {
            final Long entityId = entity.getId();
            final String stepsJson = mealRecipeMapper.writeStepsJson(allSteps);
            transactionTemplate.executeWithoutResult(status ->
                mealRecipeRepository.findById(entityId).ifPresent(r -> {
                    if ("PENDING".equals(r.getStepsStatus())) {
                        r.setStepsJson(stepsJson);
                        r.setStepsStatus("GENERATED");
                        r.setUpdatedAt(LocalDateTime.now());
                        mealRecipeRepository.save(r);
                    }
                })
            );
            log.info("Steps persisted for recipeId={} count={}", recipeId, allSteps.size());
        }
    }

    public MealCatalogResponseDTO getCatalog() {
        return mealCatalogService.getCatalog();
    }

    public MealIntentResponseDTO analyzeIntent(String sourceText) {
        return mealIntentService.toResponse(mealIntentService.analyze(sourceText));
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
        if (PREFERENCE_LIKE.equals(preference)) {
            enrichRecipeDetailsForFavorite(recipe);
        }
        recipe.setPreference(preference);
        recipe.setUpdatedAt(LocalDateTime.now());
        MealRecipe saved = mealRecipeRepository.save(recipe);

        RecipePreferenceResponseDTO response = new RecipePreferenceResponseDTO();
        response.setRecipeId(saved.getId());
        response.setPreference(saved.getPreference());
        response.setUpdatedAt(saved.getUpdatedAt() != null ? saved.getUpdatedAt() : recipe.getUpdatedAt());
        return Optional.of(response);
    }

    private void enrichRecipeDetailsForFavorite(MealRecipe recipe) {
        if (shouldFetchImageForFavorite(recipe)) {
            RecipeDTO recipeCard = mealRecipeMapper.toRecipeDTO(recipe);
            MealImageResult imageResult;
            try {
                imageResult = mealImageProvider.generate(null, recipeCard);
            } catch (MealGenerationException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new MealGenerationException("收藏前补齐菜谱图片失败，请重试", false, exception);
            }

            if (imageResult == null
                    || imageResult.getImageUrl() == null
                    || imageResult.getImageUrl().isBlank()
                    || !"GENERATED".equals(imageResult.getImageStatus())) {
                throw new MealGenerationException("收藏前补齐菜谱图片失败，请重试", false);
            }

            recipe.setImageUrl(imageResult.getImageUrl());
            recipe.setImageStatus(imageResult.getImageStatus());
        }

        if (shouldGenerateStepsForFavorite(recipe)) {
            RecipeDTO recipeCard = mealRecipeMapper.toRecipeDTO(recipe);
            List<RecipeStepDTO> generatedSteps = new ArrayList<>();
            String locale = normalizeNullableText(recipe.getLocale());

            try {
                mealGenerationProvider.streamRecipeSteps(
                        recipeCard,
                        locale == null ? "zh-CN" : locale,
                        token -> { },
                        generatedSteps::add
                );
            } catch (MealGenerationException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new MealGenerationException("收藏前补齐详细做法失败，请重试", false, exception);
            }

            if (generatedSteps.isEmpty()) {
                throw new MealGenerationException("收藏前补齐详细做法失败，请重试", false);
            }

            recipe.setStepsJson(mealRecipeMapper.writeStepsJson(generatedSteps));
            recipe.setStepsStatus("GENERATED");
        }
    }

    private boolean shouldFetchImageForFavorite(MealRecipe recipe) {
        return "PENDING".equals(recipe.getImageStatus());
    }

    private boolean shouldGenerateStepsForFavorite(MealRecipe recipe) {
        return "PENDING".equals(recipe.getStepsStatus())
                || recipe.getStepsJson() == null
                || recipe.getStepsJson().isBlank();
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
        return MealRecipeMapper.normalizeSourceText(value);
    }

    private String normalizeNullableText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private void applyIntentNormalization(MealRecommendationRequestDTO request) {
        if (request == null || request.getSourceText() == null) {
            return;
        }
        MealIntentService.MealIntentAnalysis analysis = mealIntentService.analyze(request.getSourceText());
        if (analysis.normalizedSourceText() != null && !analysis.normalizedSourceText().isBlank()) {
            request.setSourceText(analysis.normalizedSourceText());
        }
        if (request.getCatalogItemId() == null && analysis.catalogItemId() != null) {
            request.setCatalogItemId(analysis.catalogItemId());
        }
    }

    private String buildFallbackReasonSummary(MealRecommendationRequestDTO request, List<RecipeDTO> recipes) {
        if (recipes == null || recipes.isEmpty()) {
            return null;
        }
        String titles = recipes.stream()
                .map(RecipeDTO::getTitle)
                .filter(Objects::nonNull)
                .limit(3)
                .reduce((left, right) -> left + "、" + right)
                .orElse("这组菜");
        String normalizedStaple = normalizeNullableText(request.getStaple());
        String staple;
        if (normalizedStaple == null) {
            staple = "主食不限";
        } else if ("RICE".equals(normalizedStaple)) {
            staple = "米饭";
        } else if ("NOODLES".equals(normalizedStaple)) {
            staple = "面条";
        } else if ("COARSE_GRAINS".equals(normalizedStaple)) {
            staple = "粗粮";
        } else if ("NO_STAPLE".equals(normalizedStaple)) {
            staple = "不吃主食";
        } else {
            staple = request.getStaple();
        }
        return String.format(
                "根据你想吃的“%s”，先为你组合了 %s 这几道更贴近当前方向的菜，再按 %d 道菜和 %s 的设定去补齐整桌搭配。",
                request.getSourceText(),
                titles,
                request.getDishCount() == null ? recipes.size() : request.getDishCount(),
                staple
        );
    }

    /**
     * Post-generation filter: removes dishes that violate diversity constraints.
     * Rules: same core ingredient must not appear in more than 2 dishes; at most 1 soup; at most 1 cold dish.
     */
    private List<RecipeDTO> applyDishConstraints(List<RecipeDTO> recipes) {
        if (recipes == null || recipes.size() <= 1) return recipes;
        List<RecipeDTO> result = new ArrayList<>();
        Set<String> seenTitles = new java.util.HashSet<>();
        Map<String, Integer> ingredientCount = new HashMap<>();
        int soupCount = 0;
        int coldCount = 0;
        for (RecipeDTO recipe : recipes) {
            String title = recipe.getTitle() != null ? recipe.getTitle() : "";
            if (!title.isEmpty() && !seenTitles.add(title)) {
                log.info("Constraint: filtered duplicate dish title '{}'", title);
                continue;
            }
            if (isSoupDish(title) && soupCount >= 1) {
                log.info("Constraint: filtered excess soup dish '{}'", title);
                continue;
            }
            if (isColdDish(title) && coldCount >= 1) {
                log.info("Constraint: filtered excess cold dish '{}'", title);
                continue;
            }
            List<String> mainIngredients = extractMainIngredients(recipe);
            String conflicting = mainIngredients.stream()
                    .filter(ing -> ingredientCount.getOrDefault(ing, 0) >= 2)
                    .findFirst().orElse(null);
            if (conflicting != null) {
                log.info("Constraint: filtered dish '{}' - ingredient '{}' already in 2+ dishes", title, conflicting);
                continue;
            }
            if (isSoupDish(title)) soupCount++;
            if (isColdDish(title)) coldCount++;
            mainIngredients.forEach(ing -> ingredientCount.merge(ing, 1, Integer::sum));
            result.add(recipe);
        }
        if (result.size() < recipes.size()) {
            log.info("Dish constraints: {} of {} recipes retained", result.size(), recipes.size());
        }
        return result;
    }

    /**
     * Wraps a streaming recipe consumer with thread-safe diversity constraints.
     * Used for parallel multi-dish generation where callbacks arrive concurrently.
     */
    private Consumer<RecipeDTO> constrainedStreamConsumer(Long catalogItemId, int maxCount, Consumer<RecipeDTO> delegate) {
        final Object lock = new Object();
        final Set<String> seenTitles = new java.util.HashSet<>();
        final Map<String, Integer> ingredientCount = new HashMap<>();
        final int[] soupCount = {0};
        final int[] coldCount = {0};
        final int[] acceptedCount = {0};
        return recipe -> {
            String title = recipe.getTitle() != null ? recipe.getTitle() : "";
            List<String> mainIngredients = extractMainIngredients(recipe);
            synchronized (lock) {
                if (acceptedCount[0] >= maxCount) {
                    log.info("Stream constraint: filtered '{}' - already reached maxCount={}", title, maxCount);
                    return;
                }
                if (!title.isEmpty() && !seenTitles.add(title)) {
                    log.info("Stream constraint: filtered duplicate title '{}'", title);
                    return;
                }
                if (isSoupDish(title) && soupCount[0] >= 1) {
                    log.info("Stream constraint: filtered soup '{}'", title);
                    return;
                }
                if (isColdDish(title) && coldCount[0] >= 1) {
                    log.info("Stream constraint: filtered cold dish '{}'", title);
                    return;
                }
                String conflicting = mainIngredients.stream()
                        .filter(ing -> ingredientCount.getOrDefault(ing, 0) >= 2)
                        .findFirst().orElse(null);
                if (conflicting != null) {
                    log.info("Stream constraint: filtered '{}' - ingredient '{}' repeated", title, conflicting);
                    return;
                }
                if (isSoupDish(title)) soupCount[0]++;
                if (isColdDish(title)) coldCount[0]++;
                mainIngredients.forEach(ing -> ingredientCount.merge(ing, 1, Integer::sum));
                acceptedCount[0]++;
            }
            delegate.accept(recipe);
        };
    }

    private boolean isSoupDish(String title) {
        return title.contains("汤") || title.contains("羹") || title.contains("炖") || title.contains("煲");
    }

    private boolean isColdDish(String title) {
        return title.startsWith("凉拌") || title.contains("凉菜") || title.contains("冷盘");
    }

    private List<String> extractMainIngredients(RecipeDTO recipe) {
        if (recipe.getIngredients() == null) return List.of();
        return recipe.getIngredients().stream()
                .map(i -> i.getName() != null ? i.getName().trim() : "")
                .filter(name -> !name.isEmpty() && !EXCLUDED_FROM_DEDUP.contains(name))
                .map(this::normalizeIngredient)
                .distinct()
                .toList();
    }

    private String normalizeIngredient(String name) {
        return INGREDIENT_SYNONYMS.getOrDefault(name, name);
    }

    /**
     * Queries the user's recent recipe history and injects deduplicated dish titles into
     * {@code request.recentDishTitles}. The generation provider uses this list to avoid
     * recommending the same dishes repeatedly across sessions.
     *
     * <p>Fetches up to 60 most-recent titles, deduplicates, and caps at 30 for the prompt
     * to avoid excessive token usage.
     */
    private void injectRecentDishHistory(MealRecommendationRequestDTO request, Long userId) {
        if (userId == null) return;
        try {
            List<String> raw = mealRecipeRepository.findRecentTitlesByUserId(
                    userId, PageRequest.of(0, 60));
            List<String> deduped = raw.stream()
                    .filter(t -> t != null && !t.isBlank())
                    .distinct()
                    .limit(30)
                    .toList();
            if (!deduped.isEmpty()) {
                request.setRecentDishTitles(deduped);
                log.debug("Injected {} recent dish titles for diversity hint userId={}", deduped.size(), userId);
            }
        } catch (Exception e) {
            // Non-critical: generation proceeds without history if query fails
            log.warn("Failed to fetch recent dish history for userId={}", userId, e);
        }
    }
}
