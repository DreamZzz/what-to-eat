package com.quickstart.template.contexts.meal.application;

import com.quickstart.template.contexts.meal.domain.MealCatalogItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MealIntentServiceTest {
    @Mock
    private MealCatalogService mealCatalogService;

    private MealIntentService mealIntentService;

    @BeforeEach
    void setUp() {
        mealIntentService = new MealIntentService(mealCatalogService);
    }

    @Test
    @DisplayName("analyze should ask for clarification on ambiguous short input")
    void analyze_ShouldAskForClarificationOnAmbiguousShortInput() {
        when(mealCatalogService.resolveCatalogTitle("家")).thenReturn(Optional.empty());

        MealIntentService.MealIntentAnalysis analysis = mealIntentService.analyze("家");

        assertEquals(MealIntentService.MealIntentDecision.CLARIFY, analysis.decision());
        assertEquals("家常菜", analysis.normalizedSourceText());
        assertEquals("你是想吃家常菜吗？", analysis.clarificationQuestion());
        assertTrue(analysis.catalogFirst());
    }

    @Test
    @DisplayName("analyze should treat broad food keywords as catalog-first without clarification")
    void analyze_ShouldTreatBroadFoodKeywordsAsCatalogFirst() {
        when(mealCatalogService.resolveCatalogTitle("辣")).thenReturn(Optional.empty());

        MealIntentService.MealIntentAnalysis analysis = mealIntentService.analyze("辣");

        assertEquals(MealIntentService.MealIntentDecision.PROCEED, analysis.decision());
        assertEquals("辣", analysis.normalizedSourceText());
        assertTrue(analysis.catalogFirst());
    }

    @Test
    @DisplayName("analyze should resolve explicit catalog dishes directly")
    void analyze_ShouldResolveExplicitCatalogDishDirectly() {
        MealCatalogItem item = new MealCatalogItem();
        item.setId(91L);
        item.setName("鱼香肉丝");
        when(mealCatalogService.resolveCatalogTitle("鱼香肉丝")).thenReturn(Optional.of("鱼香肉丝"));
        when(mealCatalogService.findItemByTitle("鱼香肉丝")).thenReturn(Optional.of(item));

        MealIntentService.MealIntentAnalysis analysis = mealIntentService.analyze("鱼香肉丝");

        assertEquals(MealIntentService.MealIntentDecision.PROCEED, analysis.decision());
        assertEquals("鱼香肉丝", analysis.normalizedSourceText());
        assertFalse(analysis.catalogFirst());
        assertEquals(91L, analysis.catalogItemId());
    }

    @Test
    @DisplayName("analyze should use catalog suggestions to clarify other short ambiguous text")
    void analyze_ShouldUseCatalogSuggestionsForShortAmbiguousText() {
        when(mealCatalogService.resolveCatalogTitle("鲜")).thenReturn(Optional.empty());
        when(mealCatalogService.resolveRecommendedTitles("鲜", List.of(), 3, List.of()))
                .thenReturn(List.of("鲜虾蒸蛋", "鲜菇豆腐汤"));

        MealIntentService.MealIntentAnalysis analysis = mealIntentService.analyze("鲜");

        assertEquals(MealIntentService.MealIntentDecision.CLARIFY, analysis.decision());
        assertEquals("鲜虾蒸蛋、鲜菇豆腐汤", analysis.normalizedSourceText());
    }
}
