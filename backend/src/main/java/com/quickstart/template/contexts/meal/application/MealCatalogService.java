package com.quickstart.template.contexts.meal.application;

import com.quickstart.template.contexts.meal.api.dto.MealCatalogItemDTO;
import com.quickstart.template.contexts.meal.api.dto.MealCatalogResponseDTO;
import com.quickstart.template.contexts.meal.domain.MealCatalogDataset;
import com.quickstart.template.contexts.meal.domain.MealCatalogItem;
import com.quickstart.template.contexts.meal.domain.MealCatalogItemTag;
import com.quickstart.template.contexts.meal.domain.MealCatalogTag;
import com.quickstart.template.contexts.meal.infrastructure.persistence.MealCatalogDatasetRepository;
import com.quickstart.template.contexts.meal.infrastructure.persistence.MealCatalogItemRepository;
import com.quickstart.template.contexts.meal.infrastructure.persistence.MealCatalogTagRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class MealCatalogService {
    private static final Logger log = LoggerFactory.getLogger(MealCatalogService.class);
    private static final String TYPE_CATEGORY = "CATEGORY";
    private static final String TYPE_SUBCATEGORY = "SUBCATEGORY";
    private static final String TYPE_COOKING_METHOD = "COOKING_METHOD";
    private static final String TYPE_FLAVOR = "FLAVOR";
    private static final String TYPE_FEATURE = "FEATURE";
    private static final String TYPE_INGREDIENT = "INGREDIENT";
    private static final java.util.regex.Pattern TITLE_NORMALIZATION_PATTERN =
            java.util.regex.Pattern.compile("[\\s\\p{Punct}\\p{IsPunctuation}。！？、，；：…·（）()【】\\[\\]\"'“”‘’]+");

    private final MealCatalogDatasetRepository datasetRepository;
    private final MealCatalogItemRepository itemRepository;
    private final MealCatalogTagRepository tagRepository;
    private final MealCatalogMarkdownParser markdownParser;
    private final String datasetVersion;
    private final String datasetTitle;
    private final String sourceFile;

    public MealCatalogService(
            MealCatalogDatasetRepository datasetRepository,
            MealCatalogItemRepository itemRepository,
            MealCatalogTagRepository tagRepository,
            MealCatalogMarkdownParser markdownParser,
            @Value("${app.meal.catalog.dataset-version:cn-home-menu-v1}") String datasetVersion,
            @Value("${app.meal.catalog.dataset-title:300道中国常见菜分类表}") String datasetTitle,
            @Value("${app.meal.catalog.source-file:meal/catalog/chinese-home-menu-v1.md}") String sourceFile) {
        this.datasetRepository = datasetRepository;
        this.itemRepository = itemRepository;
        this.tagRepository = tagRepository;
        this.markdownParser = markdownParser;
        this.datasetVersion = datasetVersion;
        this.datasetTitle = datasetTitle;
        this.sourceFile = sourceFile;
    }

    @Transactional
    public MealCatalogDataset ensureCatalogSeeded() {
        String sourceChecksum = markdownParser.checksum(sourceFile);
        Optional<MealCatalogDataset> existing = datasetRepository.findByVersion(datasetVersion);
        if (existing.isPresent()) {
            MealCatalogDataset dataset = existing.get();
            String existingChecksum = dataset.getSourceChecksum();
            long existingItemCount = itemRepository.countByDatasetId(dataset.getId());
            if (existingChecksum != null
                    && !existingChecksum.isBlank()
                    && !Objects.equals(existingChecksum, sourceChecksum)
                    && !Objects.equals(existingChecksum, buildLegacyMetadataChecksum(dataset))) {
                throw new MealGenerationException("基础菜单版本已存在但资源内容发生变化，请提升数据集版本后再导入", true);
            }
            dataset.setSourceFile(sourceFile);
            dataset.setSourceChecksum(sourceChecksum);
            dataset.setTitle(datasetTitle);

            if (existingItemCount == 0) {
                List<MealCatalogMarkdownParser.ParsedCatalogItem> parsedItems = markdownParser.parse(sourceFile);
                dataset.setActive(true);
                dataset.setTotalItems(parsedItems.size());
                dataset.setImportedAt(LocalDateTime.now());
                datasetRepository.save(dataset);
                return importParsedItems(dataset, parsedItems);
            }
            dataset.setTotalItems((int) existingItemCount);
            if (dataset.getImportedAt() == null) {
                dataset.setImportedAt(LocalDateTime.now());
            }
            dataset.setActive(true);
            datasetRepository.save(dataset);
            activateOtherDatasets(dataset);
            return dataset;
        }

        List<MealCatalogMarkdownParser.ParsedCatalogItem> parsedItems = markdownParser.parse(sourceFile);
        MealCatalogDataset dataset = new MealCatalogDataset();
        dataset.setVersion(datasetVersion);
        dataset.setTitle(datasetTitle);
        dataset.setSourceFile(sourceFile);
        dataset.setSourceChecksum(sourceChecksum);
        dataset.setTotalItems(parsedItems.size());
        dataset.setActive(true);
        dataset.setImportedAt(LocalDateTime.now());
        MealCatalogDataset savedDataset = datasetRepository.save(dataset);
        return importParsedItems(savedDataset, parsedItems);
    }

    private MealCatalogDataset importParsedItems(
            MealCatalogDataset savedDataset,
            List<MealCatalogMarkdownParser.ParsedCatalogItem> parsedItems
    ) {
        Map<String, MealCatalogTag> tagCache = new LinkedHashMap<>();
        List<MealCatalogItem> itemsToPersist = new ArrayList<>();
        for (MealCatalogMarkdownParser.ParsedCatalogItem parsedItem : parsedItems) {
            MealCatalogItem item = new MealCatalogItem();
            item.setDataset(savedDataset);
            item.setDatasetVersion(savedDataset.getVersion());
            item.setSourceIndex(parsedItem.sourceIndex());
            item.setCode(parsedItem.code());
            item.setSlug(parsedItem.slug());
            item.setName(parsedItem.name());
            item.setCategory(parsedItem.category());
            item.setSubcategory(parsedItem.subcategory());
            item.setCookingMethod(parsedItem.cookingMethod());
            item.setRawFlavorText(parsedItem.rawFlavorText());
            item.setEnabled(true);

            linkTag(item, TYPE_CATEGORY, parsedItem.category(), tagCache);
            linkTag(item, TYPE_SUBCATEGORY, parsedItem.subcategory(), tagCache);
            linkTag(item, TYPE_COOKING_METHOD, parsedItem.cookingMethod(), tagCache);
            parsedItem.flavorTags().forEach(tag -> linkTag(item, TYPE_FLAVOR, tag, tagCache));
            parsedItem.featureTags().forEach(tag -> linkTag(item, TYPE_FEATURE, tag, tagCache));
            parsedItem.ingredientTags().forEach(tag -> linkTag(item, TYPE_INGREDIENT, tag, tagCache));

            itemsToPersist.add(item);
        }

        itemRepository.saveAll(itemsToPersist);
        activateOtherDatasets(savedDataset);
        log.info("Meal catalog seeded: version={}, items={}", savedDataset.getVersion(), itemsToPersist.size());
        return savedDataset;
    }

    @Transactional(readOnly = true)
    public Optional<MealCatalogItem> findItemById(Long catalogItemId) {
        if (catalogItemId == null) {
            return Optional.empty();
        }

        Optional<MealCatalogDataset> activeDataset = datasetRepository.findFirstByActiveTrueOrderByImportedAtDesc();
        if (activeDataset.isEmpty()) {
            return Optional.empty();
        }
        return itemRepository.findByIdAndDatasetVersionAndEnabledTrue(catalogItemId, activeDataset.get().getVersion());
    }

    @Transactional(readOnly = true)
    public MealCatalogResponseDTO getCatalog() {
        MealCatalogDataset dataset = datasetRepository.findFirstByActiveTrueOrderByImportedAtDesc()
                .orElseGet(this::ensureCatalogSeeded);
        if (itemRepository.countByDatasetId(dataset.getId()) == 0) {
            dataset = ensureCatalogSeeded();
        }
        List<MealCatalogItem> items = itemRepository.findAllByDatasetVersionAndEnabledTrueOrderBySourceIndexAsc(dataset.getVersion());

        MealCatalogResponseDTO response = new MealCatalogResponseDTO();
        response.setDatasetVersion(dataset.getVersion());
        response.setTotal(items.size());
        response.setItems(items.stream().map(this::toCatalogItemDTO).toList());
        return response;
    }

    @Transactional(readOnly = true)
    public Optional<String> resolveCatalogTitle(String candidateTitle) {
        if (candidateTitle == null || candidateTitle.isBlank()) {
            return Optional.empty();
        }

        List<MealCatalogItem> activeItems = getActiveCatalogItems();
        if (activeItems.isEmpty()) {
            return Optional.empty();
        }

        String normalizedCandidate = normalizeDishTitle(candidateTitle);
        if (normalizedCandidate.isBlank()) {
            return Optional.empty();
        }

        Map<String, String> exactTitleMap = buildExactTitleMap(activeItems);
        String exactMatch = exactTitleMap.get(normalizedCandidate);
        if (exactMatch != null) {
            return Optional.of(exactMatch);
        }

        if (normalizedCandidate.length() < 2) {
            return Optional.empty();
        }

        List<String> containsMatches = activeItems.stream()
                .map(MealCatalogItem::getName)
                .filter(Objects::nonNull)
                .filter(name -> normalizeDishTitle(name).contains(normalizedCandidate))
                .distinct()
                .limit(2)
                .toList();
        if (containsMatches.size() == 1) {
            return Optional.of(containsMatches.get(0));
        }

        return Optional.empty();
    }

    @Transactional(readOnly = true)
    public Optional<MealCatalogItem> findItemByTitle(String title) {
        if (title == null || title.isBlank()) {
            return Optional.empty();
        }
        String normalizedTitle = normalizeDishTitle(title);
        return getActiveCatalogItems().stream()
                .filter(item -> normalizedTitle.equals(normalizeDishTitle(item.getName())))
                .findFirst();
    }

    @Transactional(readOnly = true)
    public List<String> resolveRecommendedTitles(
            String sourceText,
            List<String> candidateTitles,
            int targetCount,
            List<String> requiredTitles
    ) {
        if (targetCount <= 0) {
            return List.of();
        }

        List<MealCatalogItem> activeItems = getActiveCatalogItems();
        if (activeItems.isEmpty()) {
            return candidateTitles == null ? List.of() : candidateTitles.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(title -> !title.isBlank())
                    .distinct()
                    .limit(targetCount)
                    .toList();
        }

        LinkedHashSet<String> resolved = new LinkedHashSet<>();
        List<String> required = requiredTitles == null ? List.of() : requiredTitles;
        for (String requiredTitle : required) {
            resolveCatalogTitle(requiredTitle).ifPresent(resolved::add);
        }
        if (candidateTitles != null) {
            for (String candidateTitle : candidateTitles) {
                resolveCatalogTitle(candidateTitle).ifPresent(resolved::add);
                if (resolved.size() >= targetCount) {
                    return new ArrayList<>(resolved);
                }
            }
        }

        if (resolved.size() < targetCount) {
            for (MealCatalogItem item : rankItemsByQuery(sourceText, activeItems)) {
                String title = item.getName();
                if (title == null || title.isBlank()) {
                    continue;
                }
                resolved.add(title);
                if (resolved.size() >= targetCount) {
                    break;
                }
            }
        }

        return new ArrayList<>(resolved).stream().limit(targetCount).toList();
    }

    private String buildLegacyMetadataChecksum(MealCatalogDataset dataset) {
        try {
            String payload = String.join(
                    ":",
                    dataset.getVersion() == null ? "" : dataset.getVersion(),
                    dataset.getSourceFile() == null ? "" : dataset.getSourceFile(),
                    String.valueOf(dataset.getTotalItems() == null ? 0 : dataset.getTotalItems())
            );
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte value : hash) {
                hex.append(String.format(Locale.ROOT, "%02x", value & 0xff));
            }
            return hex.toString();
        } catch (Exception exception) {
            throw new MealGenerationException("基础菜单历史元数据校验失败", true, exception);
        }
    }

    private void activateOtherDatasets(MealCatalogDataset activeDataset) {
        List<MealCatalogDataset> allDatasets = datasetRepository.findAll();
        for (MealCatalogDataset dataset : allDatasets) {
            dataset.setActive(Objects.equals(dataset.getId(), activeDataset.getId()));
        }
        datasetRepository.saveAll(allDatasets);
    }

    private void linkTag(MealCatalogItem item, String type, String label, Map<String, MealCatalogTag> tagCache) {
        if (label == null || label.isBlank()) {
            return;
        }

        MealCatalogTag tag = resolveTag(type, label, tagCache);
        item.addTag(tag);
    }

    private MealCatalogTag resolveTag(String type, String label, Map<String, MealCatalogTag> tagCache) {
        String normalizedKey = normalizeTagKey(label);
        String key = type + "::" + normalizedKey;
        if (tagCache.containsKey(key)) {
            return tagCache.get(key);
        }

        MealCatalogTag tag = tagRepository.findByTagTypeAndTagKey(type, normalizedKey)
                .orElseGet(() -> {
                    MealCatalogTag next = new MealCatalogTag();
                    next.setTagType(type);
                    next.setTagKey(normalizedKey);
                    next.setTagLabel(label.trim());
                    return tagRepository.save(next);
                });
        tagCache.put(key, tag);
        return tag;
    }

    private MealCatalogItemDTO toCatalogItemDTO(MealCatalogItem item) {
        Map<String, List<String>> tagsByType = new LinkedHashMap<>();
        for (MealCatalogItemTag itemTag : item.getItemTags()) {
            MealCatalogTag tag = itemTag.getTag();
            if (tag == null) {
                continue;
            }
            tagsByType.computeIfAbsent(tag.getTagType(), ignored -> new ArrayList<>()).add(tag.getTagLabel());
        }

        MealCatalogItemDTO dto = new MealCatalogItemDTO();
        dto.setId(item.getId());
        dto.setCode(item.getCode());
        dto.setSlug(item.getSlug());
        dto.setName(item.getName());
        dto.setCategory(item.getCategory());
        dto.setSubcategory(item.getSubcategory());
        dto.setCookingMethod(item.getCookingMethod());
        dto.setRawFlavorText(item.getRawFlavorText());
        dto.setFlavorTags(unique(tagsByType.get(TYPE_FLAVOR)));
        dto.setFeatureTags(unique(mergeTags(
                tagsByType.get(TYPE_CATEGORY),
                tagsByType.get(TYPE_SUBCATEGORY),
                tagsByType.get(TYPE_COOKING_METHOD),
                tagsByType.get(TYPE_FEATURE),
                tagsByType.get(TYPE_INGREDIENT)
        )));
        dto.setIngredientTags(unique(tagsByType.get(TYPE_INGREDIENT)));
        dto.setSourceIndex(item.getSourceIndex());
        return dto;
    }

    private List<String> unique(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(new LinkedHashSet<>(values));
    }

    @SafeVarargs
    private final List<String> mergeTags(List<String>... groups) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        for (List<String> group : groups) {
            if (group == null) {
                continue;
            }
            merged.addAll(group);
        }
        return new ArrayList<>(merged);
    }

    private String normalizeTagKey(String label) {
        return label.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "-");
    }

    private List<MealCatalogItem> getActiveCatalogItems() {
        MealCatalogDataset dataset = datasetRepository.findFirstByActiveTrueOrderByImportedAtDesc()
                .orElseGet(this::ensureCatalogSeeded);
        if (itemRepository.countByDatasetId(dataset.getId()) == 0) {
            dataset = ensureCatalogSeeded();
        }
        return itemRepository.findAllByDatasetVersionAndEnabledTrueOrderBySourceIndexAsc(dataset.getVersion());
    }

    private Map<String, String> buildExactTitleMap(List<MealCatalogItem> items) {
        Map<String, String> titleMap = new LinkedHashMap<>();
        for (MealCatalogItem item : items) {
            if (item.getName() == null || item.getName().isBlank()) {
                continue;
            }
            titleMap.putIfAbsent(normalizeDishTitle(item.getName()), item.getName());
        }
        return titleMap;
    }

    private List<MealCatalogItem> rankItemsByQuery(String sourceText, List<MealCatalogItem> items) {
        String normalizedQuery = normalizeDishTitle(sourceText);
        if (normalizedQuery.isBlank()) {
            return items.stream().limit(20).toList();
        }

        return items.stream()
                .map(item -> Map.entry(item, scoreItemForQuery(item, normalizedQuery)))
                .filter(entry -> entry.getValue() > 0)
                .sorted(Comparator
                        .<Map.Entry<MealCatalogItem, Integer>>comparingInt(Map.Entry::getValue)
                        .reversed()
                        .thenComparing(entry -> entry.getKey().getSourceIndex()))
                .map(Map.Entry::getKey)
                .toList();
    }

    private int scoreItemForQuery(MealCatalogItem item, String normalizedQuery) {
        if (item.getName() == null || item.getName().isBlank()) {
            return 0;
        }

        String normalizedName = normalizeDishTitle(item.getName());
        int score = 0;
        if (normalizedName.equals(normalizedQuery)) {
            score += 120;
        } else if (normalizedName.contains(normalizedQuery)) {
            score += 90;
        }

        if (containsNormalized(item.getCategory(), normalizedQuery)) {
            score += 25;
        }
        if (containsNormalized(item.getSubcategory(), normalizedQuery)) {
            score += 20;
        }
        if (containsNormalized(item.getCookingMethod(), normalizedQuery)) {
            score += 15;
        }
        if (containsNormalized(item.getRawFlavorText(), normalizedQuery)) {
            score += 20;
        }
        for (MealCatalogItemTag itemTag : item.getItemTags()) {
            MealCatalogTag tag = itemTag.getTag();
            if (tag != null && containsNormalized(tag.getTagLabel(), normalizedQuery)) {
                score += TYPE_INGREDIENT.equals(tag.getTagType()) ? 16 : 18;
            }
        }
        return score;
    }

    private boolean containsNormalized(String text, String normalizedQuery) {
        if (text == null || text.isBlank() || normalizedQuery == null || normalizedQuery.isBlank()) {
            return false;
        }
        return normalizeDishTitle(text).contains(normalizedQuery);
    }

    private String normalizeDishTitle(String value) {
        if (value == null) {
            return "";
        }
        return TITLE_NORMALIZATION_PATTERN.matcher(value.trim().toLowerCase(Locale.ROOT)).replaceAll("");
    }
}
