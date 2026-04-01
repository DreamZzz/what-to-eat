package com.quickstart.template.platform.provider.recipeai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiCompatibleMealGenerationProviderTest {

    @Test
    @DisplayName("isBroadIntentText should treat trailing punctuation as broad-intent noise")
    void isBroadIntentText_ShouldIgnoreTrailingPunctuation() {
        assertTrue(OpenAiCompatibleMealGenerationProvider.isBroadIntentText("川菜。"));
        assertTrue(OpenAiCompatibleMealGenerationProvider.isBroadIntentText("东北菜！"));
        assertTrue(OpenAiCompatibleMealGenerationProvider.isBroadIntentText("开胃口味…"));
    }

    @Test
    @DisplayName("isBroadIntentText should reject concrete dish names")
    void isBroadIntentText_ShouldRejectConcreteDishNames() {
        assertFalse(OpenAiCompatibleMealGenerationProvider.isBroadIntentText("番茄炒蛋"));
        assertFalse(OpenAiCompatibleMealGenerationProvider.isBroadIntentText("宫保鸡丁"));
    }

    @Test
    @DisplayName("parseExplicitDishNames should extract dish names when count matches")
    void parseExplicitDishNames_ShouldExtractMatchingDishNames() {
        // Typical inspiration bundle: 3 catalog names joined by 、
        assertEquals(
                List.of("蒸水蛋", "菌菇豆腐汤", "三鲜汤"),
                OpenAiCompatibleMealGenerationProvider.parseExplicitDishNames("蒸水蛋、菌菇豆腐汤、三鲜汤", 3)
        );
        // Works with comma separators too
        assertEquals(
                List.of("番茄炒蛋", "红烧肉"),
                OpenAiCompatibleMealGenerationProvider.parseExplicitDishNames("番茄炒蛋,红烧肉", 2)
        );
    }

    @Test
    @DisplayName("parseExplicitDishNames should return empty when count does not match")
    void parseExplicitDishNames_ShouldReturnEmptyWhenCountMismatch() {
        // 2 names but requested 3 → not explicit
        assertTrue(
                OpenAiCompatibleMealGenerationProvider.parseExplicitDishNames("番茄炒蛋、红烧肉", 3).isEmpty()
        );
        // Single dish → not triggered (requestedCount <= 1)
        assertTrue(
                OpenAiCompatibleMealGenerationProvider.parseExplicitDishNames("番茄炒蛋", 1).isEmpty()
        );
    }

    @Test
    @DisplayName("parseExplicitDishNames should return empty for free-form directions")
    void parseExplicitDishNames_ShouldReturnEmptyForFreeFormText() {
        // Free-form: no separators that match count=2
        assertTrue(
                OpenAiCompatibleMealGenerationProvider.parseExplicitDishNames("想吃点清淡的", 2).isEmpty()
        );
        // Ingredient list isn't dish names (count mismatch with 3)
        assertTrue(
                OpenAiCompatibleMealGenerationProvider.parseExplicitDishNames("番茄、鸡蛋、豆腐", 2).isEmpty()
        );
    }
}
