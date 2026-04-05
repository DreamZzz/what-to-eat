package com.quickstart.template.platform.provider.recipeai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quickstart.template.contexts.meal.api.dto.MealRecommendationRequestDTO;
import com.quickstart.template.contexts.meal.api.dto.RecipeDTO;
import com.quickstart.template.contexts.meal.api.dto.RecipeIngredientDTO;
import com.quickstart.template.contexts.meal.api.dto.RecipeStepDTO;
import com.quickstart.template.contexts.meal.api.dto.RecipeStepTokenDTO;
import com.quickstart.template.contexts.meal.application.MealCatalogService;
import com.quickstart.template.contexts.meal.application.MealGenerationException;
import com.quickstart.template.contexts.meal.application.MealGenerationResult;
import com.quickstart.template.contexts.meal.application.MealIntentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.regex.Pattern;

@Component
@ConditionalOnProperty(name = "app.meal.provider", havingValue = "openai-compatible")
public class OpenAiCompatibleMealGenerationProvider implements MealGenerationProvider {
    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleMealGenerationProvider.class);
    private static final int DEFAULT_FALLBACK_TIMEOUT_MS = 25_000;
    private static final Pattern TRAILING_BROAD_INTENT_PUNCTUATION = Pattern.compile("[\\p{Punct}\\p{IsPunctuation}。！？、，；：…·\\s]+$");

    /** Max concurrent LLM calls during streaming — balances throughput vs rate-limit risk. */
    private static final int STREAMING_CONCURRENCY = 2;

    private final RestClient primaryRestClient;
    private final RestClient fallbackRestClient;
    /** Dedicated client for per-dish streaming calls: longer timeout than the regular clients. */
    private final RestClient streamingRestClient;
    private final ObjectMapper objectMapper;
    private final MealCatalogService mealCatalogService;
    private final MealIntentService mealIntentService;
    private final String apiKey;
    private final String chatModel;
    private final String fallbackChatModel;
    private final String baseUrl;

    public OpenAiCompatibleMealGenerationProvider(
            ObjectMapper objectMapper,
            MealCatalogService mealCatalogService,
            MealIntentService mealIntentService,
            @Value("${app.meal.openai.base-url:https://api.openai.com}") String baseUrl,
            @Value("${app.meal.openai.api-key:}") String apiKey,
            @Value("${app.meal.openai.chat-model:gpt-4o-mini}") String chatModel,
            @Value("${app.meal.openai.fallback-chat-model:}") String fallbackChatModel,
            @Value("${app.meal.openai.timeout-ms:20000}") long timeoutMs,
            @Value("${app.meal.openai.fallback-timeout-ms:25000}") long fallbackTimeoutMs,
            @Value("${app.meal.openai.streaming-timeout-ms:50000}") long streamingTimeoutMs) {
        this.objectMapper = objectMapper;
        this.mealCatalogService = mealCatalogService;
        this.mealIntentService = mealIntentService;
        this.apiKey = apiKey;
        this.chatModel = chatModel;
        this.baseUrl = baseUrl;
        this.fallbackChatModel = resolveFallbackChatModel(baseUrl, chatModel, fallbackChatModel);
        this.primaryRestClient = buildRestClient(baseUrl, apiKey, timeoutMs);
        this.fallbackRestClient = buildRestClient(baseUrl, apiKey, Math.max(timeoutMs, fallbackTimeoutMs));
        this.streamingRestClient = buildRestClient(baseUrl, apiKey,
                Math.max(Math.max(timeoutMs, fallbackTimeoutMs), streamingTimeoutMs));
    }

    @Override
    public String providerName() {
        return "openai-compatible";
    }

    @Override
    public MealGenerationResult generate(MealRecommendationRequestDTO request) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new MealGenerationException("菜谱生成服务未配置 API key", true);
        }

        try {
            return generateWithFallback(request);
        } catch (MealGenerationException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("OpenAI-compatible meal generation failed", exception);
            throw new MealGenerationException("菜谱生成失败，请稍后重试", false, exception);
        }
    }

    /**
     * Streaming variant: for 2+ dishes always uses parallel per-dish generation so that
     * each recipe is emitted via {@code onRecipe} as soon as it is ready (~15s each),
     * rather than waiting for all dishes to complete in a single call (~60s+).
     */
    @Override
    public void generateStream(
            MealRecommendationRequestDTO request,
            Consumer<String> onSummary,
            Consumer<RecipeDTO> onRecipe
    ) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new MealGenerationException("菜谱生成服务未配置 API key", true);
        }
        try {
            if (request.getDishCount() != null && request.getDishCount() > 1) {
                streamMultiDishSequential(request, onSummary, onRecipe);
            } else {
                MealGenerationResult result = generate(request);
                if (result.getReasonSummary() != null && !result.getReasonSummary().isBlank()) {
                    onSummary.accept(result.getReasonSummary());
                }
                result.getRecipes().forEach(onRecipe);
            }
        } catch (MealGenerationException e) {
            throw e;
        } catch (Exception e) {
            log.error("OpenAI-compatible meal generation stream failed", e);
            throw new MealGenerationException("菜谱生成失败，请稍后重试", false, e);
        }
    }

    private void streamMultiDishSequential(
            MealRecommendationRequestDTO request,
            Consumer<String> onSummary,
            Consumer<RecipeDTO> onRecipe
    ) throws Exception {
        // Bounded-concurrency streaming: at most STREAMING_CONCURRENCY calls run at the
        // same time. This gives ~2× throughput vs fully sequential while keeping rate-limit
        // risk low. Each call uses streamingRestClient (50 s timeout) so even slow providers
        // rarely time out on individual dishes.
        ModelExecutionPlan plan = resolveStreamingModelExecutionPlan();
        int targetCount = request.getDishCount();
        // For large menus (> 3 dishes) the ingredient-diversity constraint filter may drop
        // 1-2 cards (parallel generation, cards don't know each other's ingredients).
        // Over-request by a small buffer so filtered slots can be covered by extras.
        int bufferedCount = targetCount > 3 ? targetCount + Math.min(2, targetCount - 3) : targetCount;
        List<String> titles = selectRecipeTitles(plan.client(), plan.modelName(), request, bufferedCount);
        if (titles.isEmpty()) {
            return;
        }
        String reasonSummary = generateReasonSummary(plan.client(), plan.modelName(), request, titles);
        if (reasonSummary != null && !reasonSummary.isBlank()) {
            onSummary.accept(reasonSummary);
        }
        log.info("Streaming {} recipe cards (phase-1, target={}, buffered={}, concurrency={}) with model {}",
                titles.size(), targetCount, bufferedCount, STREAMING_CONCURRENCY, plan.modelName());

        Semaphore semaphore = new Semaphore(STREAMING_CONCURRENCY);
        List<CompletableFuture<Void>> futures = IntStream.range(0, titles.size())
                .mapToObj(i -> CompletableFuture.runAsync(() -> {
                    try {
                        semaphore.acquire();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    try {
                        RecipeDTO recipe = generateCardDetailSafely(
                                plan.client(), plan.modelName(), request, titles, i);
                        if (recipe != null) {
                            onRecipe.accept(recipe);
                        }
                    } finally {
                        semaphore.release();
                    }
                }))
                .toList();

        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(170, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof MealGenerationException mge) throw mge;
            throw new MealGenerationException("菜谱生成失败，请稍后重试", false, e);
        } catch (java.util.concurrent.TimeoutException e) {
            log.warn("Streaming timed out after 170 s with {} titles — partial results emitted", titles.size());
            futures.forEach(f -> f.cancel(true));
        }
    }

    private MealGenerationResult generateWithFallback(MealRecommendationRequestDTO request) throws Exception {
        if (shouldSplitMultiDishGeneration(request)) {
            return generateMultiDishWithParallelDetails(request);
        }

        if (shouldPreferFastModel(request)) {
            log.info(
                    "Broad meal intent detected for sourceText={}, using fast model {}",
                    request.getSourceText(),
                    fallbackChatModel
            );
            return generateWithModel(fallbackRestClient, fallbackChatModel, request);
        }

        try {
            return generateWithModel(primaryRestClient, chatModel, request);
        } catch (Exception primaryException) {
            if (!shouldFallback(primaryException)) {
                throw primaryException;
            }

            log.warn(
                    "Primary meal model {} failed for sourceText={}, falling back to {}",
                    chatModel,
                    request.getSourceText(),
                    fallbackChatModel,
                    primaryException
            );
            return generateWithModel(fallbackRestClient, fallbackChatModel, request);
        }
    }

    private MealGenerationResult generateMultiDishWithParallelDetails(MealRecommendationRequestDTO request) throws Exception {
        ModelExecutionPlan plan = resolveModelExecutionPlan(request);
        List<String> recipeTitles = selectRecipeTitles(plan.client(), plan.modelName(), request);
        if (recipeTitles.isEmpty()) {
            return new MealGenerationResult(providerName(), List.of(), true, null);
        }

        log.info(
                "Meal recommendation detail generation running in parallel for {} recipes with model {}",
                recipeTitles.size(),
                plan.modelName()
        );

        List<RecipeDTO> recipes = IntStream.range(0, recipeTitles.size())
                .parallel()
                .mapToObj(index -> generateRecipeDetailSafely(plan.client(), plan.modelName(), request, recipeTitles, index))
                .filter(Objects::nonNull)
                .toList();
        if (recipes.isEmpty()) {
            throw new MealGenerationException("菜谱生成失败，请稍后重试", false);
        }
        return new MealGenerationResult(
                providerName(),
                recipes,
                false,
                generateReasonSummary(plan.client(), plan.modelName(), request, recipeTitles)
        );
    }

    private ModelExecutionPlan resolveModelExecutionPlan(MealRecommendationRequestDTO request) {
        if (shouldSplitMultiDishGeneration(request)
                && fallbackChatModel != null
                && !fallbackChatModel.isBlank()
                && !fallbackChatModel.equals(chatModel)) {
            log.info(
                    "Multi-dish request detected for sourceText={}, using fast model {} for title selection and detail generation",
                    request.getSourceText(),
                    fallbackChatModel
            );
            return new ModelExecutionPlan(fallbackRestClient, fallbackChatModel);
        }

        if (shouldPreferFastModel(request)) {
            log.info(
                    "Broad meal intent detected for sourceText={}, using fast model {}",
                    request.getSourceText(),
                    fallbackChatModel
            );
            return new ModelExecutionPlan(fallbackRestClient, fallbackChatModel);
        }
        return new ModelExecutionPlan(primaryRestClient, chatModel);
    }

    /**
     * For streaming per-dish calls, always use {@code streamingRestClient} (longer timeout)
     * and prefer the fast model to avoid reasoning-model latency under concurrent load.
     */
    private ModelExecutionPlan resolveStreamingModelExecutionPlan() {
        String model = (fallbackChatModel != null && !fallbackChatModel.isBlank()
                && !fallbackChatModel.equals(chatModel))
                ? fallbackChatModel
                : chatModel;
        return new ModelExecutionPlan(streamingRestClient, model);
    }

    /**
     * Selects recipe titles for a meal, either from the user's explicit dish list or via LLM.
     *
     * @param targetCount number of titles to request; may exceed {@code request.getDishCount()}
     *                    when the caller adds a buffer to compensate for potential filter drops
     */
    private List<String> selectRecipeTitles(
            RestClient client,
            String modelName,
            MealRecommendationRequestDTO request,
            int targetCount
    ) throws Exception {
        String sourceText = request.getSourceText();
        int dishCount = request.getDishCount() != null ? request.getDishCount() : 1;

        // Case 1: user listed EXACTLY dishCount dish names → use verbatim, skip LLM.
        List<String> explicit = parseExplicitDishNames(sourceText, dishCount);
        if (!explicit.isEmpty()) {
            List<String> resolvedExplicit = mealCatalogService.resolveRecommendedTitles(
                    sourceText,
                    explicit,
                    targetCount,
                    explicit
            );
            log.info("Title selection skipped: using explicit catalog dish names from sourceText: {}", resolvedExplicit);
            return resolvedExplicit;
        }

        // Case 2: user listed 2–(dishCount-1) dish names → those are required; LLM fills the rest.
        List<String> partialRequired = parsePartialExplicitDishNames(sourceText, dishCount);

        if (partialRequired.isEmpty() && mealIntentService.isBroadFoodIntent(sourceText)) {
            List<String> catalogFirstTitles = mealCatalogService.resolveRecommendedTitles(
                    sourceText,
                    List.of(),
                    targetCount,
                    List.of()
            );
            if (!catalogFirstTitles.isEmpty()) {
                log.info("Title selection skipped: using catalog-first titles for broad sourceText={}", sourceText);
                return catalogFirstTitles;
            }
        }

        JsonNode response = requestChatCompletion(
                client,
                modelName,
                MealGenerationPrompts.titleSelectionSystemPrompt(),
                MealGenerationPrompts.titleSelectionUserPrompt(request, targetCount, partialRequired)
        );
        JsonNode payload = objectMapper.readTree(stripCodeFence(extractAssistantContent(response)));
        JsonNode titlesNode = payload.path("titles");
        if (!titlesNode.isArray()) {
            throw new MealGenerationException("菜名推荐服务返回的数据格式不正确", false);
        }

        Set<String> uniqueTitles = new LinkedHashSet<>();
        for (JsonNode titleNode : titlesNode) {
            String title = titleNode.asText("").trim();
            if (!title.isBlank()) {
                uniqueTitles.add(title);
            }
            if (uniqueTitles.size() >= targetCount) {
                break;
            }
        }

        // Determine all dishes the user explicitly requested (must appear in the final list).
        // Case 2: partial list of dish names  Case 3: single specific dish name
        List<String> allRequired;
        if (!partialRequired.isEmpty()) {
            allRequired = partialRequired.stream()
                    .map(mealCatalogService::resolveCatalogTitle)
                    .flatMap(Optional::stream)
                    .toList();
        } else if (dishCount > 1 && sourceText != null && !MealGenerationPrompts.isBroadFlavorIntent(sourceText)) {
            allRequired = mealCatalogService.resolveCatalogTitle(sourceText.trim())
                    .map(List::of)
                    .orElseGet(List::of);
        } else {
            allRequired = List.of();
        }

        // Force-insert any required dishes the LLM omitted (placed at the front).
        List<String> missing = allRequired.stream().filter(r -> !uniqueTitles.contains(r)).toList();
        if (!missing.isEmpty()) {
            log.info("Title selection: LLM omitted required dishes {}, force-inserting", missing);
            List<String> reordered = new ArrayList<>(missing);
            for (String t : uniqueTitles) {
                if (!missing.contains(t) && reordered.size() < targetCount) {
                    reordered.add(t);
                }
            }
            return mealCatalogService.resolveRecommendedTitles(sourceText, reordered, targetCount, allRequired);
        }

        return mealCatalogService.resolveRecommendedTitles(
                sourceText,
                new ArrayList<>(uniqueTitles),
                targetCount,
                allRequired
        );
    }

    /** Overload for callers that don't need title buffering (uses dishCount as-is). */
    private List<String> selectRecipeTitles(RestClient client, String modelName, MealRecommendationRequestDTO request) throws Exception {
        return selectRecipeTitles(client, modelName, request, request.getDishCount() != null ? request.getDishCount() : 1);
    }

    private RecipeDTO generateRecipeDetailSafely(
            RestClient client,
            String modelName,
            MealRecommendationRequestDTO request,
            List<String> recipeTitles,
            int index
    ) {
        try {
            return generateRecipeDetail(client, modelName, request, recipeTitles, index);
        } catch (Exception exception) {
            log.warn(
                    "Meal detail generation failed for sourceText={} title={} at index={}",
                    request.getSourceText(),
                    recipeTitles.get(index),
                    index,
                    exception
            );
            return null;
        }
    }

    private RecipeDTO generateRecipeDetail(
            RestClient client,
            String modelName,
            MealRecommendationRequestDTO request,
            List<String> recipeTitles,
            int index
    ) throws Exception {
        JsonNode response = requestChatCompletion(
                client,
                modelName,
                MealGenerationPrompts.detailSystemPrompt(),
                MealGenerationPrompts.recipeDetailUserPrompt(request, recipeTitles, index)
        );
        List<RecipeDTO> recipes = parseRecipes(extractAssistantContent(response));
        if (recipes.isEmpty()) {
            throw new MealGenerationException("菜谱生成服务未返回有效菜谱", false);
        }
        return recipes.get(0);
    }

    /**
     * Phase-1 card-only generation for the streaming path (no steps).
     * Returns a RecipeDTO with {@code stepsStatus="PENDING"} so the frontend knows
     * to load steps via Phase-2 when the user taps "查看做法".
     */
    private RecipeDTO generateCardDetailSafely(
            RestClient client,
            String modelName,
            MealRecommendationRequestDTO request,
            List<String> recipeTitles,
            int index
    ) {
        try {
            return generateCardDetail(client, modelName, request, recipeTitles, index);
        } catch (Exception exception) {
            log.warn(
                    "Card detail generation failed for sourceText={} title={} at index={}",
                    request.getSourceText(),
                    recipeTitles.get(index),
                    index,
                    exception
            );
            return null;
        }
    }

    private RecipeDTO generateCardDetail(
            RestClient client,
            String modelName,
            MealRecommendationRequestDTO request,
            List<String> recipeTitles,
            int index
    ) throws Exception {
        String systemPrompt = MealGenerationPrompts.cardDetailSystemPrompt();
        JsonNode response = requestChatCompletion(
                client,
                modelName,
                systemPrompt,
                MealGenerationPrompts.recipeDetailUserPrompt(request, recipeTitles, index)
        );
        List<RecipeDTO> recipes = parseRecipes(extractAssistantContent(response));
        if (recipes.isEmpty()) {
            throw new MealGenerationException("菜谱生成服务未返回有效菜谱", false);
        }
        RecipeDTO card = recipes.get(0);
        String expectedTitle = recipeTitles.get(index);

        if (!expectedTitle.equals(card.getTitle())) {
            String wrongTitle = card.getTitle();
            log.warn("Card title mismatch: expected='{}' got='{}', retrying with targeted prompt",
                    expectedTitle, wrongTitle);
            // Retry once with a stripped-down prompt that anchors to exactly this dish.
            // Pass the wrong title so the model knows explicitly what NOT to generate.
            try {
                JsonNode retryResponse = requestChatCompletion(
                        client,
                        modelName,
                        systemPrompt,
                        MealGenerationPrompts.cardDetailRetryUserPrompt(request, recipeTitles, index, wrongTitle)
                );
                List<RecipeDTO> retryRecipes = parseRecipes(extractAssistantContent(retryResponse));
                if (!retryRecipes.isEmpty()) {
                    card = retryRecipes.get(0);
                }
            } catch (Exception e) {
                log.warn("Retry card generation failed for title='{}', will fall back to title override",
                        expectedTitle, e);
            }
            if (!expectedTitle.equals(card.getTitle())) {
                log.warn("Card title mismatch after retry: expected='{}' got='{}', overriding title",
                        expectedTitle, card.getTitle());
                card.setTitle(expectedTitle);
            }
        }

        // Mark steps as pending — Phase-2 will generate them on demand
        card.setStepsStatus("PENDING");
        card.setSteps(new ArrayList<>());
        return card;
    }

    /**
     * Phase-2: streams cooking steps for a single recipe using the LLM SSE API.
     * Tokens are accumulated in a {@link StepStreamParser} which calls {@code onStep}
     * for each step object the moment its closing brace is received.
     */
    @Override
    public void streamRecipeSteps(RecipeDTO recipe, String locale, Consumer<RecipeStepDTO> onStep) {
        streamRecipeSteps(recipe, locale, token -> { }, onStep);
    }

    @Override
    public void streamRecipeSteps(
            RecipeDTO recipe,
            String locale,
            Consumer<RecipeStepTokenDTO> onToken,
            Consumer<RecipeStepDTO> onStep
    ) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new MealGenerationException("菜谱生成服务未配置 API key", true);
        }

        String modelName = resolveStreamingModelExecutionPlan().modelName();
        String systemPrompt = MealGenerationPrompts.stepsSystemPrompt();
        String userPrompt = MealGenerationPrompts.stepsUserPrompt(recipe, locale);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", modelName);
        payload.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ));
        payload.put("stream", true);
        if (!modelName.toLowerCase(Locale.ROOT).contains("reasoner")) {
            payload.put("temperature", 0.7);
        }

        String requestBody;
        try {
            requestBody = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new MealGenerationException("构建步骤生成请求失败", false, e);
        }

        String fullUrl = baseUrl + resolveApiPath("/chat/completions");
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(fullUrl))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(60))
                .build();

        StepStreamParser parser = new StepStreamParser();
        try {
            HttpClient httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
            httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofLines())
                    .body()
                    .forEach(line -> {
                        if (!line.startsWith("data: ")) return;
                        String data = line.substring(6).trim();
                        if ("[DONE]".equals(data)) return;
                        try {
                            JsonNode node = objectMapper.readTree(data);
                            String token = node.at("/choices/0/delta/content").asText("");
                            if (token.isEmpty()) return;
                            StepStreamParser.ParseBatch batch = parser.append(token);
                            for (StepStreamParser.StepTokenDelta delta : batch.tokenDeltas()) {
                                RecipeStepTokenDTO tokenDto = new RecipeStepTokenDTO();
                                tokenDto.setIndex(delta.index());
                                tokenDto.setContentDelta(delta.contentDelta());
                                onToken.accept(tokenDto);
                            }
                            for (String stepJson : batch.completedStepJsons()) {
                                try {
                                    JsonNode stepNode = objectMapper.readTree(stepJson);
                                    RecipeStepDTO step = new RecipeStepDTO();
                                    step.setIndex(stepNode.path("index").asInt(0));
                                    step.setContent(stepNode.path("content").asText(""));
                                    onStep.accept(step);
                                } catch (Exception ex) {
                                    log.warn("Failed to parse streamed step JSON: {}", stepJson, ex);
                                }
                            }
                        } catch (Exception ex) {
                            // Skip malformed SSE events
                        }
                    });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MealGenerationException("步骤生成被中断", false, e);
        } catch (Exception e) {
            log.error("Step streaming failed for recipe={}", recipe.getTitle(), e);
            throw new MealGenerationException("步骤生成失败，请稍后重试", false, e);
        }
    }

    private MealGenerationResult generateWithModel(RestClient client, String modelName, MealRecommendationRequestDTO request) throws Exception {
        JsonNode response = requestChatCompletion(
                client,
                modelName,
                MealGenerationPrompts.systemPrompt(),
                MealGenerationPrompts.userPrompt(request)
        );
        String content = extractAssistantContent(response);
        List<RecipeDTO> recipes = parseRecipes(content);
        return new MealGenerationResult(
                providerName(),
                recipes,
                recipes.isEmpty(),
                generateReasonSummary(
                        client,
                        modelName,
                        request,
                        recipes.stream().map(RecipeDTO::getTitle).filter(Objects::nonNull).toList()
                )
        );
    }

    private String generateReasonSummary(
            RestClient client,
            String modelName,
            MealRecommendationRequestDTO request,
            List<String> recipeTitles
    ) {
        if (recipeTitles == null || recipeTitles.isEmpty()) {
            return null;
        }
        try {
            JsonNode response = requestChatCompletion(
                    client,
                    modelName,
                    MealGenerationPrompts.reasonSummarySystemPrompt(),
                    MealGenerationPrompts.reasonSummaryUserPrompt(request, recipeTitles)
            );
            JsonNode payload = objectMapper.readTree(stripCodeFence(extractAssistantContent(response)));
            String summary = payload.path("reasonSummary").asText("").trim();
            return summary.isBlank() ? null : summary;
        } catch (Exception exception) {
            log.warn(
                    "Reason summary generation failed for sourceText={} titles={}",
                    request.getSourceText(),
                    recipeTitles,
                    exception
            );
            return null;
        }
    }

    private JsonNode requestChatCompletion(RestClient client, String modelName, String systemPrompt, String userPrompt) {
        return client.post()
                .uri(resolveApiPath("/chat/completions"))
                .contentType(MediaType.APPLICATION_JSON)
                .body(buildPayload(modelName, systemPrompt, userPrompt))
                .retrieve()
                .body(JsonNode.class);
    }

    private Map<String, Object> buildPayload(String modelName, String systemPrompt, String userPrompt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", modelName);
        payload.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ));
        payload.put("response_format", Map.of("type", "json_object"));
        if (!modelName.toLowerCase(Locale.ROOT).contains("reasoner")) {
            // Randomize temperature per request (0.70–0.95) to break DeepSeek KV-cache
            // hits that cause the same dishes to appear across sessions.
            double temperature = 0.70 + Math.random() * 0.25;
            payload.put("temperature", temperature);
        }
        return payload;
    }

    private String extractAssistantContent(JsonNode response) {
        if (response == null) {
            throw new MealGenerationException("菜谱生成服务返回空响应", false);
        }

        JsonNode choices = response.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw new MealGenerationException("菜谱生成服务响应缺少 choices", false);
        }

        JsonNode message = choices.get(0).path("message");
        String content = readMessageContent(message.path("content"));
        if (content == null || content.isBlank()) {
            throw new MealGenerationException("菜谱生成服务响应缺少内容", false);
        }
        return content;
    }

    private String readMessageContent(JsonNode contentNode) {
        if (contentNode == null || contentNode.isMissingNode() || contentNode.isNull()) {
            return null;
        }
        if (contentNode.isTextual()) {
            return contentNode.asText(null);
        }
        if (contentNode.isArray()) {
            StringBuilder builder = new StringBuilder();
            for (JsonNode item : contentNode) {
                if (item.path("type").asText("").equalsIgnoreCase("text")) {
                    builder.append(item.path("text").asText(""));
                }
            }
            return builder.toString();
        }
        return contentNode.toString();
    }

    private List<RecipeDTO> parseRecipes(String content) throws Exception {
        String normalized = stripCodeFence(content);
        JsonNode payload = objectMapper.readTree(normalized);
        JsonNode recipesNode = payload.path("recipes");
        if (!recipesNode.isArray()) {
            throw new MealGenerationException("菜谱生成服务返回的数据格式不正确", false);
        }

        List<RecipeDTO> recipes = new ArrayList<>();
        for (JsonNode recipeNode : recipesNode) {
            RecipeDTO recipe = new RecipeDTO();
            recipe.setTitle(recipeNode.path("title").asText(""));
            recipe.setSummary(recipeNode.path("summary").asText(""));
            recipe.setEstimatedCalories(recipeNode.path("estimatedCalories").isInt()
                    ? recipeNode.path("estimatedCalories").asInt()
                    : null);
            recipe.setIngredients(parseIngredients(recipeNode.path("ingredients")));
            recipe.setSeasonings(parseIngredients(recipeNode.path("seasonings")));
            recipe.setSteps(parseSteps(recipeNode.path("steps")));
            recipe.setImageStatus("OMITTED");
            recipes.add(recipe);
        }

        return recipes;
    }

    private List<RecipeIngredientDTO> parseIngredients(JsonNode node) {
        List<RecipeIngredientDTO> ingredients = new ArrayList<>();
        if (!node.isArray()) {
            return ingredients;
        }

        for (JsonNode item : node) {
            RecipeIngredientDTO ingredient = new RecipeIngredientDTO();
            ingredient.setName(item.path("name").asText(""));
            ingredient.setAmount(item.path("amount").asText(""));
            ingredients.add(ingredient);
        }
        return ingredients;
    }

    private List<RecipeStepDTO> parseSteps(JsonNode node) {
        List<RecipeStepDTO> steps = new ArrayList<>();
        if (!node.isArray()) {
            return steps;
        }

        for (JsonNode item : node) {
            RecipeStepDTO step = new RecipeStepDTO();
            step.setIndex(item.path("index").isInt() ? item.path("index").asInt() : steps.size() + 1);
            step.setContent(item.path("content").asText(""));
            steps.add(step);
        }
        return steps;
    }

    private String stripCodeFence(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(?:json)?", "");
            trimmed = trimmed.replaceFirst("```$", "");
        }
        return trimmed.trim();
    }

    private String resolveApiPath(String path) {
        String normalizedBaseUrl = baseUrl == null ? "" : baseUrl.toLowerCase(Locale.ROOT);
        if (normalizedBaseUrl.endsWith("/v1") || normalizedBaseUrl.contains("/v1/")) {
            return path;
        }
        return "/v1" + path;
    }

    private boolean shouldFallback(Exception exception) {
        if (fallbackChatModel == null || fallbackChatModel.isBlank() || fallbackChatModel.equals(chatModel)) {
            return false;
        }

        if (exception instanceof MealGenerationException mealGenerationException) {
            return !mealGenerationException.isConfiguration();
        }

        Throwable current = exception;
        while (current != null) {
            if (current instanceof HttpTimeoutException || current instanceof java.net.SocketTimeoutException) {
                return true;
            }
            current = current.getCause();
        }

        return exception instanceof ResourceAccessException;
    }

    private boolean shouldPreferFastModel(MealRecommendationRequestDTO request) {
        if (fallbackChatModel == null || fallbackChatModel.isBlank() || fallbackChatModel.equals(chatModel)) {
            return false;
        }
        if (request == null || request.getCatalogItemId() != null || request.getSourceText() == null) {
            return false;
        }

        return mealIntentService.isBroadFoodIntent(request.getSourceText());
    }

    private boolean shouldSplitMultiDishGeneration(MealRecommendationRequestDTO request) {
        // Only split for very large batches (6+ dishes). For 2-5 dishes a single LLM
        // call is more reliable: concurrent parallel calls share the same API key and
        // are frequently rate-limited or timed out by the provider, causing all detail
        // calls to fail simultaneously and producing an empty result.
        return request != null
                && request.getDishCount() != null
                && request.getDishCount() > 5;
    }

    /**
     * Detects whether {@code sourceText} is an explicit enumeration of dish names whose
     * count exactly matches {@code requestedCount}.
     *
     * <p>Example: {@code "蒸水蛋、菌菇豆腐汤、三鲜汤"} with requestedCount=3 → returns
     * {@code ["蒸水蛋", "菌菇豆腐汤", "三鲜汤"]}.
     *
     * <p>Returns an empty list when the text looks like a free-form direction (e.g.
     * "想吃点清淡的" or "番茄牛腩") rather than a dish-name list.
     */
    static List<String> parseExplicitDishNames(String sourceText, Integer requestedCount) {
        if (sourceText == null || requestedCount == null || requestedCount <= 1) {
            return List.of();
        }
        // Split on common Chinese/English enumeration separators
        String[] parts = sourceText.split("[、，,；;]");
        if (parts.length != requestedCount) {
            return List.of();
        }
        List<String> names = Arrays.stream(parts)
                .map(String::trim)
                .toList();
        // Each segment must look like a dish name: 2–15 chars, no sentence starters
        boolean allLookLikeDishNames = names.stream().allMatch(s ->
                s.length() >= 2 && s.length() <= 15 && !s.isEmpty()
                        && !s.startsWith("我") && !s.startsWith("想") && !s.startsWith("不")
                        && !s.startsWith("要") && !s.startsWith("吃") && !s.startsWith("做")
        );
        return allLookLikeDishNames ? names : List.of();
    }

    /**
     * Detects a <em>partial</em> explicit dish list: the user named 2+ specific dishes but
     * fewer than {@code requestedCount}, expecting the remainder to be auto-filled.
     *
     * <p>Example: {@code "番茄炒蛋、红烧肉"} with requestedCount=5
     * → {@code ["番茄炒蛋", "红烧肉"]} (2 required, 3 auto-filled by the LLM).
     *
     * <p>Returns an empty list when segments don't look like dish names, or when the
     * segment count equals requestedCount (handled by {@link #parseExplicitDishNames}).
     */
    static List<String> parsePartialExplicitDishNames(String sourceText, Integer requestedCount) {
        if (sourceText == null || requestedCount == null || requestedCount <= 2) {
            return List.of();
        }
        String[] parts = sourceText.split("[、，,；;]");
        // "Partial" = 2+ segments, strictly fewer than the requested count
        if (parts.length < 2 || parts.length >= requestedCount) {
            return List.of();
        }
        List<String> names = Arrays.stream(parts).map(String::trim).toList();
        boolean allLookLikeDishNames = names.stream().allMatch(s ->
                s.length() >= 2 && s.length() <= 15 && !s.isEmpty()
                        && !s.startsWith("我") && !s.startsWith("想") && !s.startsWith("不")
                        && !s.startsWith("要") && !s.startsWith("吃") && !s.startsWith("做")
        );
        return allLookLikeDishNames ? names : List.of();
    }

    public static boolean isBroadIntentText(String sourceText) {
        if (sourceText == null) {
            return false;
        }

        String normalized = TRAILING_BROAD_INTENT_PUNCTUATION
                .matcher(sourceText.trim())
                .replaceAll("");
        if (normalized.isBlank()) {
            return false;
        }

        return normalized.endsWith("菜") || normalized.endsWith("口味") || normalized.endsWith("风味");
    }

    private RestClient buildRestClient(String baseUrl, String apiKey, long timeoutMs) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(buildRequestFactory(timeoutMs))
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    private SimpleClientHttpRequestFactory buildRequestFactory(long timeoutMs) {
        int normalizedReadTimeoutMs = normalizeTimeout(timeoutMs, 20_000);
        int normalizedConnectTimeoutMs = Math.min(normalizedReadTimeoutMs, 5_000);
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(normalizedConnectTimeoutMs);
        requestFactory.setReadTimeout(normalizedReadTimeoutMs);
        return requestFactory;
    }

    private int normalizeTimeout(long timeoutMs, int fallbackMs) {
        if (timeoutMs <= 0) {
            return fallbackMs;
        }
        return (int) Math.min(timeoutMs, Integer.MAX_VALUE);
    }

    private String resolveFallbackChatModel(String baseUrl, String primaryChatModel, String configuredFallbackChatModel) {
        if (configuredFallbackChatModel != null && !configuredFallbackChatModel.isBlank()) {
            return configuredFallbackChatModel.trim();
        }

        String normalizedBaseUrl = baseUrl == null ? "" : baseUrl.toLowerCase(Locale.ROOT);
        if (normalizedBaseUrl.contains("deepseek.com") && primaryChatModel != null
                && primaryChatModel.toLowerCase(Locale.ROOT).contains("reasoner")) {
            return "deepseek-chat";
        }

        return "";
    }

    private record ModelExecutionPlan(RestClient client, String modelName) {
    }

    /**
     * Stateful parser for streaming step tokens.
     * Accumulates LLM delta tokens and emits complete step JSON strings as soon as
     * each {@code {"index":N,"content":"..."}} object's closing brace is received.
     */
    static final class StepStreamParser {
        private static final java.util.regex.Pattern INDEX_PATTERN =
                java.util.regex.Pattern.compile("\"index\"\\s*:\\s*(\\d+)");
        private static final java.util.regex.Pattern CONTENT_PATTERN =
                java.util.regex.Pattern.compile("\"content\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)");

        private final StringBuilder buffer = new StringBuilder();
        private boolean foundStepsArray = false;
        private int arraySearchFrom = 0;
        private int scanFrom = 0;
        private int depth = 0;
        private int stepStart = -1;
        private Integer currentStepIndex = null;
        private String emittedContent = "";

        /**
         * Append a new token and return any newly completed step JSON strings.
         */
        ParseBatch append(String token) {
            buffer.append(token);
            List<String> completed = new ArrayList<>();
            List<StepTokenDelta> deltas = new ArrayList<>();

            if (!foundStepsArray) {
                // Guard against re-scanning already-checked content when "steps" spans tokens
                int from = Math.max(0, arraySearchFrom - 7);
                String buf = buffer.toString();
                int stepsIdx = buf.indexOf("\"steps\"", from);
                if (stepsIdx < 0) {
                    arraySearchFrom = buffer.length();
                    return new ParseBatch(deltas, completed);
                }
                int bracketIdx = buf.indexOf('[', stepsIdx + 7);
                if (bracketIdx < 0) {
                    arraySearchFrom = stepsIdx;
                    return new ParseBatch(deltas, completed);
                }
                foundStepsArray = true;
                scanFrom = bracketIdx + 1;
            }

            // Scan newly arrived characters for complete {…} step objects
            for (int i = scanFrom; i < buffer.length(); i++) {
                char c = buffer.charAt(i);
                if (c == '{') {
                    if (depth == 0) {
                        stepStart = i;
                        currentStepIndex = null;
                        emittedContent = "";
                    }
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0 && stepStart >= 0) {
                        completed.add(buffer.substring(stepStart, i + 1));
                        stepStart = -1;
                        currentStepIndex = null;
                        emittedContent = "";
                    }
                }
            }
            scanFrom = buffer.length();
            if (stepStart >= 0) {
                String partial = buffer.substring(stepStart);
                if (currentStepIndex == null) {
                    java.util.regex.Matcher indexMatcher = INDEX_PATTERN.matcher(partial);
                    if (indexMatcher.find()) {
                        currentStepIndex = Integer.parseInt(indexMatcher.group(1));
                    }
                }
                java.util.regex.Matcher contentMatcher = CONTENT_PATTERN.matcher(partial);
                if (contentMatcher.find()) {
                    String decoded = decodeJsonString(contentMatcher.group(1));
                    if (decoded.length() > emittedContent.length()) {
                        String delta = decoded.substring(emittedContent.length());
                        if (!delta.isEmpty()) {
                            deltas.add(new StepTokenDelta(currentStepIndex != null ? currentStepIndex : 0, delta));
                            emittedContent = decoded;
                        }
                    }
                }
            }
            return new ParseBatch(deltas, completed);
        }

        private String decodeJsonString(String value) {
            return value
                    .replace("\\n", "\n")
                    .replace("\\t", "\t")
                    .replace("\\r", "\r")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\");
        }

        record StepTokenDelta(int index, String contentDelta) {
        }

        record ParseBatch(List<StepTokenDelta> tokenDeltas, List<String> completedStepJsons) {
        }
    }
}
