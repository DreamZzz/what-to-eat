package com.quickstart.template.platform.provider.recipeai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
}
