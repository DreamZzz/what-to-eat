package com.quickstart.template.contexts.meal.application;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Component
public class MealCatalogMarkdownParser {
    private static final Set<String> FLAVOR_TOKENS = Set.of(
            "酸辣", "咸鲜", "酸鲜", "香辣", "蒜香", "清淡", "鲜香", "微辣", "酱香", "鱼香", "清鲜",
            "清爽", "酸甜", "甜咸", "麻辣", "麻酱", "葱香", "姜香", "豆豉", "椒麻", "黑椒", "孜然",
            "五香", "清苦", "浓香", "鲜甜", "糊辣荔枝味", "腊香", "米香", "菌香", "腐乳香", "山味",
            "豆香", "豉汁", "清香"
    );
    private static final List<String> INGREDIENT_KEYWORDS = List.of(
            "西兰花", "杏鲍菇", "油麦菜", "空心菜", "小白菜", "土豆", "白菜", "包菜", "生菜", "菠菜",
            "莴笋", "菜花", "四季豆", "豆角", "山药", "香菇", "平菇", "西葫芦", "青椒", "尖椒", "芥蓝",
            "菜心", "茄子", "番茄", "西红柿", "黄瓜", "苦瓜", "木耳", "鸡蛋", "豆腐", "香干", "豆皮",
            "豆腐皮", "腐竹", "豆芽", "韭菜", "韭黄", "蒜苗", "蒜薹", "芹菜", "洋葱", "荷兰豆",
            "牛腩", "牛肉", "肥牛", "牛杂", "羊肉", "排骨", "猪蹄", "猪耳", "猪肉", "肉丝", "肉片",
            "鸡翅", "鸡丁", "鸡块", "鸡肉", "鸡爪", "鸭翅", "鸭肉", "鸭", "鱼片", "鲫鱼", "带鱼",
            "黄鱼", "鱼", "虾仁", "大虾", "虾", "蟹", "蛤蜊", "扇贝", "海蜇", "鱿鱼", "墨鱼",
            "海米", "冬瓜", "莲藕", "萝卜", "玉米", "茶树菇", "粉条", "粉丝", "腊肉", "娃娃菜",
            "金针菇", "丝瓜", "海带", "藕片", "扁豆", "油豆角", "冬笋", "毛豆", "油麦菜"
    );

    public List<ParsedCatalogItem> parse(String resourcePath) {
        List<ParsedCatalogItem> items = new ArrayList<>();

        try {
            ClassPathResource resource = new ClassPathResource(resourcePath);
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    ParsedCatalogItem item = parseDataLine(line);
                    if (item != null) {
                        items.add(item);
                    }
                }
            }
        } catch (Exception exception) {
            throw new MealGenerationException("基础菜单资源解析失败", true, exception);
        }

        return items;
    }

    public String checksum(String resourcePath) {
        try {
            ClassPathResource resource = new ClassPathResource(resourcePath);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream inputStream = resource.getInputStream()) {
                byte[] buffer = new byte[4096];
                int read;
                while ((read = inputStream.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            StringBuilder hex = new StringBuilder();
            for (byte value : digest.digest()) {
                hex.append(String.format(Locale.ROOT, "%02x", value & 0xff));
            }
            return hex.toString();
        } catch (Exception exception) {
            throw new MealGenerationException("基础菜单资源校验失败", true, exception);
        }
    }

    private ParsedCatalogItem parseDataLine(String line) {
        String trimmed = line == null ? "" : line.trim();
        if (!trimmed.startsWith("|") || trimmed.contains("---") || trimmed.contains("序号")) {
            return null;
        }

        String content = trimmed.substring(1, trimmed.length() - 1);
        String[] cells = Arrays.stream(content.split("\\|"))
                .map(String::trim)
                .toArray(String[]::new);
        if (cells.length < 6 || !cells[0].matches("\\d+")) {
            return null;
        }

        int sourceIndex = Integer.parseInt(cells[0]);
        String name = cells[1];
        String category = cells[2];
        String subcategory = cells[3];
        String cookingMethod = cells[4];
        String rawFlavorText = cells[5];

        LinkedHashSet<String> flavorTags = new LinkedHashSet<>();
        LinkedHashSet<String> featureTags = new LinkedHashSet<>();
        for (String token : splitTokens(rawFlavorText)) {
            if (isFlavorTag(token)) {
                flavorTags.add(token);
            } else {
                featureTags.add(token);
            }
        }

        LinkedHashSet<String> ingredientTags = new LinkedHashSet<>(extractIngredientTags(name));
        ingredientTags.addAll(extractIngredientTags(subcategory));
        ingredientTags.addAll(extractIngredientTags(category));

        return new ParsedCatalogItem(
                sourceIndex,
                String.format(Locale.ROOT, "cn-home-%03d", sourceIndex),
                buildSlug(name, sourceIndex),
                name,
                category,
                subcategory,
                cookingMethod,
                rawFlavorText,
                new ArrayList<>(flavorTags),
                new ArrayList<>(featureTags),
                new ArrayList<>(ingredientTags)
        );
    }

    private List<String> splitTokens(String rawFlavorText) {
        if (rawFlavorText == null || rawFlavorText.isBlank()) {
            return List.of();
        }
        return Arrays.stream(rawFlavorText.split("[、,，/；;]+"))
                .map(String::trim)
                .filter(token -> !token.isEmpty())
                .toList();
    }

    private boolean isFlavorTag(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        if (FLAVOR_TOKENS.contains(token)) {
            return true;
        }

        return token.contains("香")
                || token.contains("鲜")
                || token.contains("辣")
                || token.contains("酸")
                || token.contains("甜")
                || token.contains("苦")
                || token.contains("麻")
                || token.contains("咸")
                || token.contains("味");
    }

    private List<String> extractIngredientTags(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        LinkedHashSet<String> tags = new LinkedHashSet<>();
        for (String keyword : INGREDIENT_KEYWORDS) {
            if (text.contains(keyword)) {
                tags.add(keyword);
            }
        }
        return new ArrayList<>(tags);
    }

    private String buildSlug(String name, int sourceIndex) {
        String normalized = name == null ? "" : name.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", "-")
                .replaceAll("[^\\p{IsHan}\\p{IsLetter}\\p{IsDigit}-]", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
        if (normalized.isBlank()) {
            normalized = "catalog";
        }
        return normalized + "-" + String.format(Locale.ROOT, "%03d", sourceIndex);
    }

    public record ParsedCatalogItem(
            int sourceIndex,
            String code,
            String slug,
            String name,
            String category,
            String subcategory,
            String cookingMethod,
            String rawFlavorText,
            List<String> flavorTags,
            List<String> featureTags,
            List<String> ingredientTags
    ) {
    }
}
