package com.quickstart.template.platform.provider.recipeai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quickstart.template.contexts.meal.api.dto.MealRecommendationRequestDTO;
import com.quickstart.template.contexts.meal.api.dto.RecipeDTO;
import com.quickstart.template.contexts.meal.api.dto.RecipeIngredientDTO;
import com.quickstart.template.contexts.meal.api.dto.RecipeStepDTO;
import com.quickstart.template.contexts.meal.application.MealGenerationException;
import com.quickstart.template.contexts.meal.application.MealGenerationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;

import java.net.http.HttpTimeoutException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.regex.Pattern;

@Component
@ConditionalOnProperty(name = "app.meal.provider", havingValue = "openai-compatible")
public class OpenAiCompatibleMealGenerationProvider implements MealGenerationProvider {
    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleMealGenerationProvider.class);
    private static final int DEFAULT_FALLBACK_TIMEOUT_MS = 25_000;
    private static final Pattern TRAILING_BROAD_INTENT_PUNCTUATION = Pattern.compile("[\\p{Punct}\\p{IsPunctuation}。！？、，；：…·\\s]+$");

    private final RestClient primaryRestClient;
    private final RestClient fallbackRestClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String chatModel;
    private final String fallbackChatModel;
    private final String baseUrl;

    public OpenAiCompatibleMealGenerationProvider(
            ObjectMapper objectMapper,
            @Value("${app.meal.openai.base-url:https://api.openai.com}") String baseUrl,
            @Value("${app.meal.openai.api-key:}") String apiKey,
            @Value("${app.meal.openai.chat-model:gpt-4o-mini}") String chatModel,
            @Value("${app.meal.openai.fallback-chat-model:}") String fallbackChatModel,
            @Value("${app.meal.openai.timeout-ms:20000}") long timeoutMs,
            @Value("${app.meal.openai.fallback-timeout-ms:25000}") long fallbackTimeoutMs) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.chatModel = chatModel;
        this.baseUrl = baseUrl;
        this.fallbackChatModel = resolveFallbackChatModel(baseUrl, chatModel, fallbackChatModel);
        this.primaryRestClient = buildRestClient(baseUrl, apiKey, timeoutMs);
        this.fallbackRestClient = buildRestClient(baseUrl, apiKey, Math.max(timeoutMs, fallbackTimeoutMs));
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
            return new MealGenerationResult(providerName(), List.of(), true);
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
        return new MealGenerationResult(providerName(), recipes, false);
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

    private List<String> selectRecipeTitles(RestClient client, String modelName, MealRecommendationRequestDTO request) throws Exception {
        JsonNode response = requestChatCompletion(
                client,
                modelName,
                buildTitleSelectionSystemPrompt(),
                buildTitleSelectionUserPrompt(request)
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
            if (uniqueTitles.size() >= request.getDishCount()) {
                break;
            }
        }

        return new ArrayList<>(uniqueTitles);
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
                buildRecipeDetailSystemPrompt(),
                buildRecipeDetailUserPrompt(request, recipeTitles, index)
        );
        List<RecipeDTO> recipes = parseRecipes(extractAssistantContent(response));
        if (recipes.isEmpty()) {
            throw new MealGenerationException("菜谱生成服务未返回有效菜谱", false);
        }
        return recipes.get(0);
    }

    private MealGenerationResult generateWithModel(RestClient client, String modelName, MealRecommendationRequestDTO request) throws Exception {
        JsonNode response = requestChatCompletion(
                client,
                modelName,
                buildSystemPrompt(),
                buildUserPrompt(request)
        );
        String content = extractAssistantContent(response);
        List<RecipeDTO> recipes = parseRecipes(content);
        return new MealGenerationResult(providerName(), recipes, recipes.isEmpty());
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
            payload.put("temperature", 0.7);
        }
        return payload;
    }

    private String buildSystemPrompt() {
        return """
                你是一个中文家常菜谱生成器。
                请只返回 JSON，不要输出 markdown 或额外解释。
                JSON 结构必须是：
                {
                  "recipes": [
                    {
                      "title": "菜名",
                      "summary": "一句话介绍",
                      "estimatedCalories": 420,
                      "ingredients": [{"name": "食材", "amount": "100g"}],
                      "seasonings": [{"name": "佐料", "amount": "适量"}],
                      "steps": [{"index": 1, "content": "步骤"}]
                    }
                  ]
                }
                """;
    }

    private String buildTitleSelectionSystemPrompt() {
        return """
                你是一个中文菜谱推荐助手。
                请只返回 JSON，不要输出 markdown 或额外解释。
                JSON 结构必须是：
                {
                  "titles": ["菜名1", "菜名2"]
                }
                要求：
                1. titles 必须是不同的、适合家常烹饪的中文菜名。
                2. 不要返回主食、饮品或泛泛类别词。
                3. 数量必须严格等于用户要求的道数。
                """;
    }

    private String buildRecipeDetailSystemPrompt() {
        return """
                你是一个中文家常菜谱生成器。
                请只返回 JSON，不要输出 markdown 或额外解释。
                JSON 结构必须是：
                {
                  "recipes": [
                    {
                      "title": "菜名",
                      "summary": "一句话介绍",
                      "estimatedCalories": 420,
                      "ingredients": [{"name": "食材", "amount": "100g"}],
                      "seasonings": [{"name": "佐料", "amount": "适量"}],
                      "steps": [{"index": 1, "content": "步骤"}]
                    }
                  ]
                }
                要求：
                1. recipes 数组只能有 1 项。
                2. title 必须与用户指定的菜名一致。
                """;
    }

    private String buildUserPrompt(MealRecommendationRequestDTO request) {
        return String.format(
                Locale.ROOT,
                "根据输入文本“%s”，生成%d道菜谱。总热量控制在%d千卡以内，主食=%s，口味=%s，语言=%s。",
                request.getSourceText(),
                request.getDishCount(),
                request.getTotalCalories(),
                request.getStaple(),
                request.getFlavor(),
                request.getLocale() == null ? "zh-CN" : request.getLocale()
        );
    }

    private String buildTitleSelectionUserPrompt(MealRecommendationRequestDTO request) {
        return String.format(
                Locale.ROOT,
                "根据输入文本“%s”，推荐%d道不同的中文家常菜名。总热量控制在%d千卡以内，主食=%s，口味=%s，语言=%s。只返回菜名数组，不要返回做法、食材或解释。",
                request.getSourceText(),
                request.getDishCount(),
                request.getTotalCalories(),
                request.getStaple(),
                request.getFlavor(),
                request.getLocale() == null ? "zh-CN" : request.getLocale()
        );
    }

    private String buildRecipeDetailUserPrompt(MealRecommendationRequestDTO request, List<String> recipeTitles, int index) {
        int requestedDishCount = recipeTitles.isEmpty() ? 1 : recipeTitles.size();
        int perDishCalories = request.getTotalCalories() == null || request.getTotalCalories() <= 0
                ? 0
                : Math.max(1, request.getTotalCalories() / requestedDishCount);
        return String.format(
                Locale.ROOT,
                "输入方向是“%s”。本次菜单共%d道菜，第%d道菜固定为“%s”。本菜建议热量控制在%d千卡以内，主食=%s，口味=%s，语言=%s。请只生成这一道菜的详细菜谱，并保证 title 与指定菜名完全一致。",
                request.getSourceText(),
                requestedDishCount,
                index + 1,
                recipeTitles.get(index),
                perDishCalories,
                request.getStaple(),
                request.getFlavor(),
                request.getLocale() == null ? "zh-CN" : request.getLocale()
        );
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

        return isBroadIntentText(request.getSourceText());
    }

    private boolean shouldSplitMultiDishGeneration(MealRecommendationRequestDTO request) {
        return request != null
                && request.getDishCount() != null
                && request.getDishCount() > 1;
    }

    static boolean isBroadIntentText(String sourceText) {
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
}
