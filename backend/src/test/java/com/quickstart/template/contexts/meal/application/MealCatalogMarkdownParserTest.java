package com.quickstart.template.contexts.meal.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MealCatalogMarkdownParserTest {
    private final MealCatalogMarkdownParser parser = new MealCatalogMarkdownParser();

    @Test
    @DisplayName("markdown parser should load all 300 catalog dishes")
    void parse_ShouldLoadAllCatalogDishes() {
        List<MealCatalogMarkdownParser.ParsedCatalogItem> items =
                parser.parse("meal/catalog/chinese-home-menu-v1.md");

        assertEquals(300, items.size());
        assertEquals("酸辣土豆丝", items.get(0).name());
        assertEquals("豆豉鲮鱼油麦菜", items.get(299).name());
    }

    @Test
    @DisplayName("markdown parser should derive ingredient and flavor tags")
    void parse_ShouldDeriveIngredientAndFlavorTags() {
        List<MealCatalogMarkdownParser.ParsedCatalogItem> items =
                parser.parse("meal/catalog/chinese-home-menu-v1.md");

        MealCatalogMarkdownParser.ParsedCatalogItem tomatoEgg =
                items.stream().filter(item -> "番茄炒蛋".equals(item.name())).findFirst().orElseThrow();

        assertTrue(tomatoEgg.flavorTags().contains("酸甜"));
        assertTrue(tomatoEgg.ingredientTags().contains("番茄"));
        assertTrue(tomatoEgg.ingredientTags().contains("鸡蛋"));
    }
}
