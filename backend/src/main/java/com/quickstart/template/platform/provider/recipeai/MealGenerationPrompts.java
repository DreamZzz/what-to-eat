package com.quickstart.template.platform.provider.recipeai;

import com.quickstart.template.contexts.meal.api.dto.MealRecommendationRequestDTO;
import com.quickstart.template.contexts.meal.api.dto.RecipeDTO;
import com.quickstart.template.contexts.meal.api.dto.RecipeIngredientDTO;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Central home for all LLM prompt text used by {@link OpenAiCompatibleMealGenerationProvider}.
 *
 * <p>Keeping prompts here—separate from HTTP plumbing and JSON parsing—means prompt
 * engineering iterations only touch this file. When updating a prompt, also update
 * the "Prompt version" comment so reviewers can see what changed and why.
 */
final class MealGenerationPrompts {

    private MealGenerationPrompts() {
    }

    /**
     * Predefined diversity angles appended to LLM prompts to break KV-cache hits at DeepSeek.
     * A random angle is picked per request so that even identical system prompts produce
     * different KV-cache keys, preventing the model from returning the same dishes repeatedly.
     */
    private static final String[] DIVERSITY_ANGLES = {
        "（本次优先推荐快手菜，20分钟内可完成）",
        "（本次优先推荐低油少盐的健康做法）",
        "（本次优先推荐传统家常经典口味）",
        "（本次优先推荐葱姜蒜爆香风格）",
        "（本次偏向推荐色彩鲜艳、视觉好看的菜肴）",
        "（本次优先推荐应季时蔬的做法）",
        "（本次偏向推荐鲜嫩口感、火候精确的菜）",
        "（本次优先推荐家常但不常见的做法）",
    };

    /** Picks a random diversity angle to append to user prompts and break LLM KV cache. */
    static String pickDiversityAngle() {
        return DIVERSITY_ANGLES[(int) (Math.random() * DIVERSITY_ANGLES.length)];
    }

    /** 主食枚举 → 中文标签（用于拼入 LLM prompt，避免模型猜测英文 enum 含义）。 */
    private static final java.util.Map<String, String> STAPLE_LABELS = java.util.Map.of(
            "RICE",          "米饭",
            "NOODLES",       "面条",
            "COARSE_GRAINS", "粗粮（糙米/玉米/薯类等）",
            "NO_STAPLE",     "不吃主食"
    );

    /**
     * 主食参考热量（kcal/人份），用于从总热量中扣除主食热量后再分配给推荐菜品。
     * 数值基于常见家庭用量经验值，无需每次调用 AI 查询。
     * NO_STAPLE = 0，全部热量分配给菜品。
     */
    private static final java.util.Map<String, Integer> STAPLE_CALORIES = java.util.Map.of(
            "RICE",          280,   // 约150g干米煮熟≈300g，~280kcal
            "NOODLES",       300,   // 约100g干面条，~300kcal
            "COARSE_GRAINS", 250,   // 粗粮热量密度略低，~250kcal
            "NO_STAPLE",     0
    );

    private static String stapleLabel(String staple) {
        return staple == null ? "不限" : STAPLE_LABELS.getOrDefault(staple.toUpperCase(Locale.ROOT), staple);
    }

    /** 返回主食参考热量（kcal），未知枚举默认 280。 */
    static int stapleCalories(String staple) {
        if (staple == null) return 0;
        return STAPLE_CALORIES.getOrDefault(staple.toUpperCase(Locale.ROOT), 280);
    }

    /**
     * 从总热量中扣除主食热量后，按菜品数量均分，返回每道菜的建议热量上限。
     * 最低保底 100kcal，避免生成无意义的极低热量菜谱。
     */
    static int perDishCalories(Integer totalCalories, String staple, int dishCount) {
        if (totalCalories == null || totalCalories <= 0 || dishCount <= 0) return 0;
        int adjusted = Math.max(0, totalCalories - stapleCalories(staple));
        return Math.max(100, adjusted / dishCount);
    }

    // ---------------------------------------------------------------------------
    // Prompt version: v2 (2026-03-31)
    // v1: basic JSON schema, no richness constraints
    // v2: quantified amounts, knife-work sizes, oil-temp visual cues, fire levels,
    //     time+state markers per step, beginner tips as final step.
    //     Guided by cooker-prompts.md (20-year head-chef SOP style).
    // ---------------------------------------------------------------------------

    /**
     * System prompt for single-call generation (≤5 dishes, all in one response).
     */
    static String systemPrompt() {
        return recipeSystemPrompt(false);
    }

    /**
     * System prompt for the per-dish detail call in multi-dish parallel generation (6+ dishes).
     */
    static String detailSystemPrompt() {
        return recipeSystemPrompt(true);
    }

    /**
     * System prompt for the title-selection call (first step of multi-dish generation).
     * Intentionally lightweight—only dish names are needed here.
     */
    static String titleSelectionSystemPrompt() {
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
                4. 食材多样性：同一种核心食材（如猪肉、鸡蛋、豆腐、番茄）不得在超过 2 道菜名中出现。
                5. 汤类菜（菜名含"汤""羹""炖""煲"）至多 1 道；凉菜（菜名含"凉拌""冷盘"）至多 1 道。
                6. 烹饪手法多样化：同一批菜中，相同的烹饪动词（炒/蒸/炖/煎/炸/拌）不超过 2 道。
                7. 历史多样性：用户曾经推荐过的菜名会在用户提示的末尾列出，本次选择必须完全回避这些菜名，优先推荐用户从未见过的菜肴。
                """;
    }

    static String reasonSummarySystemPrompt() {
        return """
                你是一个中文点餐推荐助手。
                你的任务是根据用户输入的方向、主食偏好、热量目标和已选菜名，用 1 到 2 句话总结“为什么推荐这几道菜”。
                请只返回 JSON，不要输出 markdown 或额外解释。
                JSON 结构必须是：
                {
                  "reasonSummary": "一句到两句的中文解释"
                }
                要求：
                1. 语言自然，像产品提示文案，不要像模型自我介绍。
                2. 需要点出至少一个和用户输入直接相关的理由。
                3. 如果有多道菜，强调搭配感、口味层次或荤素平衡。
                4. 不要编造用户没输入过的健康承诺。
                """;
    }

    /**
     * User turn for single-call generation.
     */
    static String userPrompt(MealRecommendationRequestDTO request) {
        int dishCount = request.getDishCount() == null ? 1 : request.getDishCount();
        int perDish = perDishCalories(request.getTotalCalories(), request.getStaple(), dishCount);
        StringBuilder sb = new StringBuilder(String.format(
                Locale.ROOT,
                "\u201c%s\u201d，生成%d道菜谱。每道菜热量控制在%d千卡以内（已扣除主食%s约%dkcal），语言=%s。",
                request.getSourceText(),
                dishCount,
                perDish,
                stapleLabel(request.getStaple()),
                stapleCalories(request.getStaple()),
                request.getLocale() == null ? "zh-CN" : request.getLocale()
        ));
        List<String> recentTitles = request.getRecentDishTitles();
        if (recentTitles != null && !recentTitles.isEmpty()) {
            // If sourceText is a specific dish name the user explicitly wants, exclude it
            // from history avoidance — otherwise the history rule blocks the user's own request.
            String src = request.getSourceText();
            List<String> filteredTitles = !isBroadFlavorIntent(src)
                    ? recentTitles.stream().filter(t -> !t.equals(src)).collect(Collectors.toList())
                    : recentTitles;
            if (!filteredTitles.isEmpty()) {
                sb.append("【历史回避】以下菜名该用户最近已推荐过，本次严禁重复：")
                  .append(String.join("、", filteredTitles))
                  .append("。");
            }
        }
        return sb.toString();
    }

    static String reasonSummaryUserPrompt(
            MealRecommendationRequestDTO request,
            List<String> recipeTitles
    ) {
        String locale = request.getLocale() == null ? "zh-CN" : request.getLocale();
        String staple = stapleLabel(request.getStaple());
        String titles = recipeTitles == null || recipeTitles.isEmpty()
                ? "暂无菜名"
                : String.join("、", recipeTitles);
        return String.format(
                Locale.ROOT,
                "用户输入方向：%s。推荐菜名：%s。菜数：%d。总热量目标：%s 千卡。主食偏好：%s。语言：%s。请输出推荐这几道菜的简短理由。",
                request.getSourceText(),
                titles,
                request.getDishCount() == null ? 1 : request.getDishCount(),
                request.getTotalCalories() == null ? "未设置" : request.getTotalCalories(),
                staple,
                locale
        );
    }

    /**
     * User turn for title selection (first step of multi-dish sequential generation).
     *
     * <p>Three cases are handled:
     * <ul>
     *   <li><b>Partial explicit list</b> (e.g. "番茄炒蛋、红烧肉" with dishCount=5): the user
     *       named 2–(N-1) specific dishes; all must appear in titles and LLM fills the rest.</li>
     *   <li><b>Single specific dish</b> (e.g. "红烧肉"): must appear as the first title;
     *       remaining slots are filled with complementary dishes.</li>
     *   <li><b>Broad flavor intent</b> (e.g. "想吃点清淡的"): LLM freely picks all dishes.</li>
     * </ul>
     */
    static String titleSelectionUserPrompt(MealRecommendationRequestDTO request) {
        return titleSelectionUserPrompt(request, request.getDishCount() == null ? 1 : request.getDishCount(), List.of());
    }

    /** Overload without partialRequired — backwards-compatible for callers that don't need it. */
    static String titleSelectionUserPrompt(MealRecommendationRequestDTO request, int targetCount) {
        return titleSelectionUserPrompt(request, targetCount, List.of());
    }

    /**
     * @param targetCount     number of titles to request; may exceed {@code request.getDishCount()}
     *                        when the caller adds a buffer for filter resilience
     * @param partialRequired dish names the user explicitly listed (fewer than dishCount);
     *                        all must appear in the LLM-returned title list
     */
    static String titleSelectionUserPrompt(
            MealRecommendationRequestDTO request,
            int targetCount,
            List<String> partialRequired
    ) {
        int dishCount = request.getDishCount() == null ? 1 : request.getDishCount();
        int perDish = perDishCalories(request.getTotalCalories(), request.getStaple(), dishCount);
        String sourceText = request.getSourceText();
        String locale = request.getLocale() == null ? "zh-CN" : request.getLocale();
        String staple = stapleLabel(request.getStaple());

        String directive;
        // Build the set of dish names that must NOT be blocked by history avoidance
        java.util.Set<String> requiredSet = new java.util.HashSet<>();

        if (partialRequired != null && !partialRequired.isEmpty()) {
            // Case: user listed 2+ specific dishes, LLM fills the remaining slots
            int remainingCount = Math.max(0, targetCount - partialRequired.size());
            directive = String.format(
                    Locale.ROOT,
                    "以下%d道菜是用户明确指定的必选菜品，必须全部出现在 titles 中：\u300c%s\u300d；"
                            + "再推荐%d道与之搭配的家常菜，保证整体食材和烹饪方式多样。",
                    partialRequired.size(),
                    String.join("\u300d\u300c", partialRequired),
                    remainingCount
            );
            requiredSet.addAll(partialRequired);
        } else if (targetCount > 1 && !isBroadFlavorIntent(sourceText)) {
            // Case: user named a single specific dish that must be the first title
            int remainingCount = targetCount - 1;
            directive = String.format(
                    Locale.ROOT,
                    "\u201c%s\u201d是用户明确想要的菜，必须作为菜单中的第一道菜出现在 titles 中；"
                            + "再推荐%d道与之搭配的家常菜，保证整体食材和烹饪方式多样。",
                    sourceText,
                    remainingCount
            );
            requiredSet.add(sourceText.trim());
        } else {
            // Case: broad flavor intent — LLM picks all dishes freely
            directive = String.format(
                    Locale.ROOT,
                    "根据用户口味方向\u201c%s\u201d，推荐%d道不同的中文家常菜名。",
                    sourceText,
                    targetCount
            );
        }

        StringBuilder sb = new StringBuilder(directive);
        sb.append(String.format(
                Locale.ROOT,
                "每道菜热量控制在%d千卡以内，主食偏好=%s，语言=%s。只返回菜名数组，不要返回做法、食材或解释。",
                perDish, staple, locale
        ));

        // History avoidance: exclude all user-required dish names so the rule cannot block them.
        List<String> recentTitles = request.getRecentDishTitles();
        if (recentTitles != null && !recentTitles.isEmpty()) {
            final java.util.Set<String> finalRequiredSet = requiredSet;
            List<String> filteredTitles = recentTitles.stream()
                    .filter(t -> !finalRequiredSet.contains(t))
                    .collect(Collectors.toList());
            if (!filteredTitles.isEmpty()) {
                sb.append("【历史回避】以下是该用户最近推荐过的菜名，本次严禁重复：")
                  .append(String.join("、", filteredTitles))
                  .append("。");
            }
        }

        // Append a random diversity angle to break DeepSeek KV-cache on repeated calls
        sb.append(pickDiversityAngle());

        return sb.toString();
    }

    /**
     * Returns true when the source text expresses a broad flavor direction rather than a
     * specific dish name. Examples: "想吃点清淡的" (ends with a category word), "家常菜".
     *
     * <p>Heuristics: ends with a broad category suffix, or is longer than 10 characters
     * (suggesting a sentence rather than a dish name), or starts with a first-person pattern.
     */
    static boolean isBroadFlavorIntent(String text) {
        if (text == null || text.isBlank()) return true;
        String t = text.trim();
        if (t.length() > 10) return true;
        if (t.startsWith("我") || t.startsWith("想") || t.startsWith("来") || t.startsWith("随")) return true;
        return t.endsWith("菜") || t.endsWith("口味") || t.endsWith("风味") || t.endsWith("的");
    }

    /**
     * User turn for per-dish detail generation.
     *
     * @param recipeTitles full title list for this meal, so the model understands the overall menu
     * @param index        0-based index of the dish being generated
     */
    static String recipeDetailUserPrompt(
            MealRecommendationRequestDTO request,
            List<String> recipeTitles,
            int index
    ) {
        int requestedDishCount = recipeTitles.isEmpty() ? 1 : recipeTitles.size();
        int perDish = perDishCalories(request.getTotalCalories(), request.getStaple(), requestedDishCount);
        String currentTitle = recipeTitles.get(index);
        String sourceText = request.getSourceText();

        // When the user's input text is itself a specific dish name that appears in the
        // title list, framing it as "输入方向" in detail calls for OTHER dishes confuses
        // the model — it tends to generate that dish again instead of following the
        // fixed-title constraint. In that case, suppress the redundant source reference.
        String sourceClause;
        if (recipeTitles.contains(sourceText) && !sourceText.equals(currentTitle)) {
            sourceClause = "";
        } else {
            sourceClause = "输入方向是\u201c" + sourceText + "\u201d。";
        }

        // Calorie budget: give the total remaining budget (after staple) rather than the
        // per-dish average, so the model calculates estimatedCalories from actual ingredients
        // instead of anchoring to the average.
        int totalCalories = request.getTotalCalories() != null ? request.getTotalCalories() : 0;
        int stapleKcal = stapleCalories(request.getStaple());
        int remainingKcal = Math.max(0, totalCalories - stapleKcal);

        // List the other dishes in the menu so the model can pick ingredients that
        // don't conflict with them. The constraint filter in MealService rejects cards
        // whose main ingredient already appears in ≥2 accepted dishes, so proactively
        // guiding the model here reduces false-positive filtering for large menus.
        String otherTitlesClause = "";
        if (requestedDishCount > 1) {
            String others = recipeTitles.stream()
                    .filter(t -> !t.equals(currentTitle))
                    .collect(Collectors.joining("、"));
            if (!others.isBlank()) {
                otherTitlesClause = "菜单中的其他菜品为「" + others + "」，本菜的核心食材（猪肉/鸡肉/牛肉/鱼/鸡蛋/豆腐等）须与它们有所不同，避免重复。";
            }
        }

        return String.format(
                Locale.ROOT,
                "%s本次菜单共%d道菜，第%d道菜固定为\u201c%s\u201d。"
                        + "%s"
                        + "全餐菜品热量预算约%dkcal（总%dkcal已扣除主食%s约%dkcal）；"
                        + "本菜 estimatedCalories 须按实际食材克重估算真实热量，禁止取各道菜均值，"
                        + "荤菜主料为肉类约300-600kcal、蔬菜为主约80-200kcal。"
                        + "主食偏好=%s，语言=%s。"
                        + "请只生成这一道菜的详细菜谱，并保证 title 与指定菜名完全一致。%s",
                sourceClause,
                requestedDishCount,
                index + 1,
                currentTitle,
                otherTitlesClause,
                remainingKcal,
                totalCalories,
                stapleLabel(request.getStaple()),
                stapleKcal,
                stapleLabel(request.getStaple()),
                request.getLocale() == null ? "zh-CN" : request.getLocale(),
                pickDiversityAngle()
        );
    }

    /**
     * Retry user prompt for card detail generation — used when the first attempt returns a
     * wrong dish title. This prompt is deliberately stripped down and laser-focused on the
     * single expected title to leave the model no ambiguity about what to generate.
     *
     * @param wrongTitle the dish name the model mistakenly returned on the first attempt;
     *                   included in the prompt so the model explicitly knows to avoid it
     */
    static String cardDetailRetryUserPrompt(
            MealRecommendationRequestDTO request,
            List<String> recipeTitles,
            int index,
            String wrongTitle
    ) {
        int requestedDishCount = recipeTitles.isEmpty() ? 1 : recipeTitles.size();
        int perDish = perDishCalories(request.getTotalCalories(), request.getStaple(), requestedDishCount);
        String currentTitle = recipeTitles.get(index);
        String avoidClause = (wrongTitle != null && !wrongTitle.isBlank() && !wrongTitle.equals(currentTitle))
                ? "（上次错误返回了\u300c" + wrongTitle + "\u300d，本次严禁再生成该菜品）"
                : "";
        return String.format(
                Locale.ROOT,
                "【专项任务】%s只为菜名\u300c%s\u300d生成一张菜谱卡片，title 字段必须精确为\u300c%s\u300d，"
                        + "不得生成任何其他菜品。热量控制在%d千卡以内，主食偏好=%s，语言=%s。%s",
                avoidClause,
                currentTitle,
                currentTitle,
                perDish,
                stapleLabel(request.getStaple()),
                request.getLocale() == null ? "zh-CN" : request.getLocale(),
                pickDiversityAngle()
        );
    }

    /**
     * System prompt for Phase-1 card generation (streaming path, per-dish detail call).
     * Asks only for title, summary, estimatedCalories, ingredients, seasonings — no steps.
     * This makes each card arrive ~40% faster; steps are fetched on-demand in Phase 2.
     */
    static String cardDetailSystemPrompt() {
        return """
                你是一位拥有 20 年经验的中餐主厨。
                你的任务是生成家常菜的卡片信息（不包含烹饪步骤）。
                请只返回 JSON，不要输出 markdown 代码块或任何额外解释。
                JSON 结构必须是：
                {
                  "recipes": [
                    {
                      "title": "菜名",
                      "summary": "一句话，包含核心烹饪技法和成品口感特点（如：大火爆炒保留脆嫩，酱香浓郁回甜）",
                      "estimatedCalories": 420,
                      "ingredients": [{"name": "食材", "amount": "150g"}],
                      "seasonings": [{"name": "佐料", "amount": "5g（约1矿泉水瓶盖）"}]
                    }
                  ]
                }

                卡片质量要求（严格执行）：
                1. 用量精确化：食材与佐料必须给出克数或家庭常用量（如"5g盐，约1个矿泉水瓶盖"），严禁出现"适量"或"少许"。
                2. 热量真实估算：estimatedCalories 必须根据食材克重和烹饪方式估算真实热量，严禁填写平均值；荤菜约300-600kcal、蔬菜约80-200kcal、汤类约100-300kcal。
                3. recipes 数组只能有 1 项，title 必须与用户指定的菜名完全一致。
                """;
    }

    /**
     * System prompt for Phase-2 steps generation (streaming token-by-token via LLM SSE).
     * Returns only a {@code steps} array so the bracket-depth parser can detect each
     * completed step object as tokens arrive.
     */
    static String stepsSystemPrompt() {
        return """
                你是一位拥有 20 年经验的中餐主厨，是严谨的烹饪 SOP 作者。
                你的任务是把专业厨艺翻译成新手能执行的"工业级标准操作流程"。
                请只返回 JSON，不要输出 markdown 代码块或任何额外解释。
                JSON 结构必须是：
                {
                  "steps": [{"index": 1, "content": "步骤，含火力/时间/视觉状态"}]
                }

                步骤质量要求（严格执行）：
                1. 刀工规格化：凡涉及切配，必须给出尺寸（如"切成3cm见方的块"、"切成约2mm粗细的火柴棍丝"）。
                2. 油温视觉化：涉及热油步骤时，须给出视觉/听觉特征（如"油温约180℃/七成热：油面微微波动，竹筷插入四周迅速冒大气泡"）。
                3. 火力与技法明确：每个炒制/煎/炸步骤须标注火力（大火/中火/文火），使用中餐专业动词（煸炒/爆香/焖制/熘/勾芡等）。
                4. 时间+状态双重标记：每个步骤须同时给出时间（秒或分钟）和可观测的物理变化（颜色/质地/气味/声音）。
                5. 新手防坑提示：在 steps 末尾追加1步（index最大+1），content格式固定为"【新手提示】①…②…③…"，列出该菜3个最常见失误及预防方法。
                """;
    }

    /**
     * User turn for Phase-2 steps generation. Passes the recipe title and ingredients
     * so the model can tailor steps to the actual ingredients used.
     */
    static String stepsUserPrompt(RecipeDTO recipe, String locale) {
        String effectiveLocale = locale != null ? locale : "zh-CN";
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT, "菜名：《%s》。", recipe.getTitle()));

        if (recipe.getIngredients() != null && !recipe.getIngredients().isEmpty()) {
            String ingList = recipe.getIngredients().stream()
                    .map(i -> i.getName() + " " + i.getAmount())
                    .collect(Collectors.joining("、"));
            sb.append("食材：").append(ingList).append("。");
        }

        if (recipe.getSeasonings() != null && !recipe.getSeasonings().isEmpty()) {
            String seaList = recipe.getSeasonings().stream()
                    .map(i -> i.getName() + " " + i.getAmount())
                    .collect(Collectors.joining("、"));
            sb.append("调料：").append(seaList).append("。");
        }

        sb.append(String.format(Locale.ROOT, "语言=%s。", effectiveLocale));
        sb.append("只返回 steps 数组，不要重复输出 title、ingredients 等信息。");
        return sb.toString();
    }

    // ---------------------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------------------

    /**
     * Shared recipe system prompt with SOP-level richness constraints.
     *
     * @param singleDishOnly when true, adds a constraint that recipes array must contain exactly 1 item
     */
    private static String recipeSystemPrompt(boolean singleDishOnly) {
        String singleDishRule = singleDishOnly
                ? "\n7. recipes 数组只能有 1 项，title 必须与用户指定的菜名完全一致。"
                : "";

        return """
                你是一位拥有 20 年经验的中餐主厨，同时是严谨的烹饪 SOP 作者。
                你的任务是把专业厨艺经验翻译成新手能执行的"工业级标准操作流程"。
                请只返回 JSON，不要输出 markdown 代码块或任何额外解释。
                JSON 结构必须是：
                {
                  "recipes": [
                    {
                      "title": "菜名",
                      "summary": "一句话，包含核心烹饪技法和成品口感特点（如：大火爆炒保留脆嫩，酱香浓郁回甜）",
                      "estimatedCalories": 420,
                      "ingredients": [{"name": "食材", "amount": "150g"}],
                      "seasonings": [{"name": "佐料", "amount": "5g（约1矿泉水瓶盖）"}],
                      "steps": [{"index": 1, "content": "步骤，含火力/时间/视觉状态"}]
                    }
                  ]
                }

                菜谱质量要求（严格执行，不达标则重写）：
                1. 用量精确化：食材与佐料必须给出克数或家庭常用量（如"5g盐，约1个矿泉水瓶盖"、"10ml生抽，约2汤匙"），严禁出现"适量"或"少许"。
                2. 刀工规格化：步骤中凡涉及切配，必须给出尺寸（如"切成3cm见方的块"、"切成约2mm粗细的火柴棍丝"）。
                3. 油温视觉化：涉及热油步骤时，不得只写"几成热"，须给出视觉/听觉特征（如"油温约180℃/七成热：油面微微波动，竹筷插入四周迅速冒大气泡"）。
                4. 火力与技法明确：每个炒制/煎/炸步骤须标注火力（大火/中火/文火），并使用中餐专业动词（煸炒/爆香/焖制/熘/勾芡等）。
                5. 时间+状态双重标记：每个步骤须同时给出时间（秒或分钟）和可观测的物理变化（颜色/质地/气味/声音），例如"中火煸炒2分钟，直到五花肉出油、表面微焦变金黄"。
                6. 新手防坑提示：在 steps 末尾追加1步（index最大+1），content格式固定为"【新手提示】①…②…③…"，列出该菜3个最常见失误及预防方法。
                7. 多菜食材多样性：同一核心食材（如猪肉、鸡蛋、豆腐、番茄）不得在超过 2 道菜中使用；汤类菜（含"汤""羹""炖""煲"）至多 1 道；凉菜（含"凉拌""冷盘"）至多 1 道。
                8. 热量真实估算：estimatedCalories 必须根据 ingredients 中的实际食材克重和烹饪方式计算真实热量，严禁填写平均值或热量上限；多道菜之间的 estimatedCalories 必须体现差异（以肉类为主约300-700kcal，纯蔬菜约80-200kcal，汤类约100-300kcal）。\
                """
                + singleDishRule;
    }
}
