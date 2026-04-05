package com.quickstart.template.contexts.meal.application;

import com.quickstart.template.contexts.meal.api.dto.MealIntentResponseDTO;
import com.quickstart.template.contexts.meal.domain.MealCatalogItem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class MealIntentService {
    private static final Pattern TRAILING_BROAD_INTENT_PUNCTUATION =
            Pattern.compile("[\\p{Punct}\\p{IsPunctuation}。！？、，；：…·\\s]+$");
    private static final Set<String> BROAD_RELATED_SINGLE_TOKENS = Set.of(
            "辣", "汤", "面", "饭", "肉", "菜", "粥"
    );
    private static final Set<String> BROAD_RELATED_PHRASES = Set.of(
            "家常", "家常菜", "清淡", "开胃", "下饭", "鲜香", "辣味", "香辣", "酸辣",
            "汤类", "汤菜", "面食", "米饭", "粗粮", "减脂", "低卡", "宵夜", "早餐", "晚餐"
    );
    private static final Map<String, String> AMBIGUOUS_EXPANSIONS = Map.of(
            "家", "家常菜"
    );

    private final MealCatalogService mealCatalogService;

    public MealIntentService(MealCatalogService mealCatalogService) {
        this.mealCatalogService = mealCatalogService;
    }

    @Transactional(readOnly = true)
    public MealIntentAnalysis analyze(String sourceText) {
        String normalizedSource = sourceText == null ? "" : sourceText.trim();
        if (normalizedSource.isBlank()) {
            return new MealIntentAnalysis(
                    MealIntentDecision.CLARIFY,
                    "",
                    "你是想让我推荐一道家常菜，还是想按食材和口味来搭配？",
                    false,
                    null
            );
        }

        Optional<String> exactTitle = mealCatalogService.resolveCatalogTitle(normalizedSource);
        if (exactTitle.isPresent()) {
            Optional<MealCatalogItem> exactItem = mealCatalogService.findItemByTitle(exactTitle.get());
            return new MealIntentAnalysis(
                    MealIntentDecision.PROCEED,
                    exactTitle.get(),
                    null,
                    false,
                    exactItem.map(MealCatalogItem::getId).orElse(null)
            );
        }

        if (isBroadFoodIntent(normalizedSource)) {
            return new MealIntentAnalysis(
                    MealIntentDecision.PROCEED,
                    normalizedSource,
                    null,
                    true,
                    null
            );
        }

        String lowerSource = normalizedSource.toLowerCase(Locale.ROOT);
        String clarification = AMBIGUOUS_EXPANSIONS.get(lowerSource);
        if (clarification != null) {
            return new MealIntentAnalysis(
                    MealIntentDecision.CLARIFY,
                    clarification,
                    String.format(Locale.ROOT, "你是想吃%s吗？", clarification),
                    true,
                    null
            );
        }

        List<String> suggestedTitles = mealCatalogService.resolveRecommendedTitles(
                normalizedSource,
                List.of(),
                3,
                List.of()
        );
        if (!suggestedTitles.isEmpty() && normalizedSource.length() <= 2) {
            String suggestion = buildSuggestionPhrase(normalizedSource, suggestedTitles);
            return new MealIntentAnalysis(
                    MealIntentDecision.CLARIFY,
                    suggestion,
                    String.format(Locale.ROOT, "你是想吃%s吗？", suggestion),
                    true,
                    null
            );
        }

        return new MealIntentAnalysis(
                MealIntentDecision.PROCEED,
                normalizedSource,
                null,
                false,
                null
        );
    }

    public MealIntentResponseDTO toResponse(MealIntentAnalysis analysis) {
        MealIntentResponseDTO response = new MealIntentResponseDTO();
        response.setDecision(analysis.decision().name());
        response.setNormalizedSourceText(analysis.normalizedSourceText());
        response.setClarificationQuestion(analysis.clarificationQuestion());
        response.setCatalogFirst(analysis.catalogFirst());
        response.setCatalogItemId(analysis.catalogItemId());
        return response;
    }

    public boolean isBroadFoodIntent(String sourceText) {
        if (sourceText == null) {
            return false;
        }
        String normalized = sourceText.trim();
        if (normalized.isBlank()) {
            return false;
        }
        if (BROAD_RELATED_SINGLE_TOKENS.contains(normalized)) {
            return true;
        }
        if (BROAD_RELATED_PHRASES.contains(normalized)) {
            return true;
        }
        return hasBroadIntentSuffix(normalized)
                || BROAD_RELATED_PHRASES.stream().anyMatch(normalized::contains);
    }

    private boolean hasBroadIntentSuffix(String sourceText) {
        String normalized = TRAILING_BROAD_INTENT_PUNCTUATION
                .matcher(sourceText.trim())
                .replaceAll("");
        if (normalized.isBlank()) {
            return false;
        }
        return normalized.endsWith("菜") || normalized.endsWith("口味") || normalized.endsWith("风味");
    }

    private String buildSuggestionPhrase(String sourceText, List<String> suggestedTitles) {
        if (sourceText != null && sourceText.contains("家")) {
            return "家常菜";
        }
        return suggestedTitles.size() >= 2
                ? suggestedTitles.get(0) + "、" + suggestedTitles.get(1)
                : suggestedTitles.get(0);
    }

    public enum MealIntentDecision {
        PROCEED,
        CLARIFY
    }

    public record MealIntentAnalysis(
            MealIntentDecision decision,
            String normalizedSourceText,
            String clarificationQuestion,
            boolean catalogFirst,
            Long catalogItemId
    ) {
    }
}
