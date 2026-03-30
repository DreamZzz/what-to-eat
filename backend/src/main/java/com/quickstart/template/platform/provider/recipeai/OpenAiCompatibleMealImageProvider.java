package com.quickstart.template.platform.provider.recipeai;

import com.fasterxml.jackson.databind.JsonNode;
import com.quickstart.template.contexts.meal.api.dto.MealRecommendationRequestDTO;
import com.quickstart.template.contexts.meal.api.dto.RecipeDTO;
import com.quickstart.template.contexts.meal.application.MealImageResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
@ConditionalOnProperty(name = "app.meal.image.provider", havingValue = "openai-compatible")
public class OpenAiCompatibleMealImageProvider implements MealImageProvider {
    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleMealImageProvider.class);

    private final RestClient restClient;
    private final String apiKey;
    private final String imageModel;
    private final String baseUrl;

    public OpenAiCompatibleMealImageProvider(
            @Value("${app.meal.openai.base-url:https://api.openai.com}") String baseUrl,
            @Value("${app.meal.openai.api-key:}") String apiKey,
            @Value("${app.meal.openai.image-model:gpt-image-1}") String imageModel,
            @Value("${app.meal.openai.timeout-ms:20000}") long timeoutMs) {
        this.apiKey = apiKey;
        this.imageModel = imageModel;
        this.baseUrl = baseUrl;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    @Override
    public String providerName() {
        return "openai-compatible";
    }

    @Override
    public MealImageResult generate(MealRecommendationRequestDTO request, RecipeDTO recipe) {
        if (apiKey == null || apiKey.isBlank()) {
            return new MealImageResult(providerName(), null, "FAILED");
        }

        try {
            JsonNode response = restClient.post()
                    .uri(resolveApiPath("/images/generations"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "model", imageModel,
                            "prompt", buildPrompt(request, recipe),
                            "size", "1024x1024"
                    ))
                    .retrieve()
                    .body(JsonNode.class);

            String imageUrl = extractImageUrl(response);
            if (imageUrl == null || imageUrl.isBlank()) {
                return new MealImageResult(providerName(), null, "FAILED");
            }
            return new MealImageResult(providerName(), imageUrl, "GENERATED");
        } catch (Exception exception) {
            log.warn("OpenAI-compatible meal image generation failed for recipe {}", recipe.getTitle(), exception);
            return new MealImageResult(providerName(), null, "FAILED");
        }
    }

    private String buildPrompt(MealRecommendationRequestDTO request, RecipeDTO recipe) {
        return String.format(
                "A beautiful Chinese home-cooking dish photo for %s. Summary: %s. Style: clean, warm, realistic, appetizing.",
                recipe.getTitle(),
                recipe.getSummary() == null ? request.getSourceText() : recipe.getSummary()
        );
    }

    private String extractImageUrl(JsonNode response) {
        if (response == null) {
            return null;
        }

        JsonNode data = response.path("data");
        if (data.isArray() && !data.isEmpty()) {
            JsonNode first = data.get(0);
            String url = first.path("url").asText(null);
            if (url != null && !url.isBlank()) {
                return url;
            }
            String b64 = first.path("b64_json").asText(null);
            if (b64 != null && !b64.isBlank()) {
                return "data:image/png;base64," + b64;
            }
        }

        String directUrl = response.path("url").asText(null);
        return directUrl != null && !directUrl.isBlank() ? directUrl : null;
    }

    private String resolveApiPath(String path) {
        String normalizedBaseUrl = baseUrl == null ? "" : baseUrl.toLowerCase(java.util.Locale.ROOT);
        if (normalizedBaseUrl.endsWith("/v1") || normalizedBaseUrl.contains("/v1/")) {
            return path;
        }
        return "/v1" + path;
    }
}
