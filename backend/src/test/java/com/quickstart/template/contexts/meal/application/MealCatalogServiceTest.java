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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MealCatalogServiceTest {
    @Mock
    private MealCatalogDatasetRepository datasetRepository;

    @Mock
    private MealCatalogItemRepository itemRepository;

    @Mock
    private MealCatalogTagRepository tagRepository;

    @Mock
    private MealCatalogMarkdownParser markdownParser;

    private MealCatalogService mealCatalogService;

    @BeforeEach
    void setUp() {
        mealCatalogService = new MealCatalogService(
                datasetRepository,
                itemRepository,
                tagRepository,
                markdownParser,
                "cn-home-menu-v1",
                "300道中国常见菜分类表",
                "meal/catalog/chinese-home-menu-v1.md"
        );
    }

    @Test
    @DisplayName("ensureCatalogSeeded should be idempotent when dataset version and checksum match")
    void ensureCatalogSeeded_ShouldBeIdempotentWhenChecksumMatches() {
        MealCatalogDataset dataset = new MealCatalogDataset();
        dataset.setId(1L);
        dataset.setVersion("cn-home-menu-v1");
        dataset.setSourceChecksum("checksum-a");
        dataset.setActive(false);

        when(markdownParser.checksum("meal/catalog/chinese-home-menu-v1.md")).thenReturn("checksum-a");
        when(datasetRepository.findByVersion("cn-home-menu-v1")).thenReturn(Optional.of(dataset));
        when(datasetRepository.findAll()).thenReturn(List.of(dataset));
        when(datasetRepository.save(any(MealCatalogDataset.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(itemRepository.countByDatasetId(1L)).thenReturn(1L);

        MealCatalogDataset result = mealCatalogService.ensureCatalogSeeded();

        assertSame(dataset, result);
        verify(markdownParser, never()).parse(any());
        verify(itemRepository, never()).saveAll(any());
        verify(datasetRepository).save(dataset);
    }

    @Test
    @DisplayName("ensureCatalogSeeded should upgrade legacy checksum metadata when dataset already exists")
    void ensureCatalogSeeded_ShouldUpgradeLegacyChecksumMetadata() {
        MealCatalogDataset dataset = new MealCatalogDataset();
        dataset.setId(1L);
        dataset.setVersion("cn-home-menu-v1");
        dataset.setSourceFile("meal/catalog/chinese-home-menu-v1.md");
        dataset.setSourceChecksum("7ebbf01d9d516dc62bce83d93c91828b");
        dataset.setTitle("旧标题");
        dataset.setTotalItems(300);
        dataset.setActive(false);

        when(markdownParser.checksum("meal/catalog/chinese-home-menu-v1.md")).thenReturn("checksum-upgraded");
        when(datasetRepository.findByVersion("cn-home-menu-v1")).thenReturn(Optional.of(dataset));
        when(datasetRepository.findAll()).thenReturn(List.of(dataset));
        when(datasetRepository.save(any(MealCatalogDataset.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(itemRepository.countByDatasetId(1L)).thenReturn(300L);

        MealCatalogDataset result = mealCatalogService.ensureCatalogSeeded();

        assertSame(dataset, result);
        assertEquals("checksum-upgraded", dataset.getSourceChecksum());
        assertEquals("300道中国常见菜分类表", dataset.getTitle());
        verify(datasetRepository).save(argThat(saved ->
                "checksum-upgraded".equals(saved.getSourceChecksum())
                        && "300道中国常见菜分类表".equals(saved.getTitle())
        ));
        verify(markdownParser, never()).parse(any());
    }

    @Test
    @DisplayName("ensureCatalogSeeded should import parsed items and attach structured tags")
    void ensureCatalogSeeded_ShouldImportParsedItems() {
        MealCatalogMarkdownParser.ParsedCatalogItem parsedItem = new MealCatalogMarkdownParser.ParsedCatalogItem(
                51,
                "cn-home-051",
                "番茄炒蛋-051",
                "番茄炒蛋",
                "蛋豆",
                "鸡蛋",
                "炒",
                "酸甜、国民家常",
                List.of("酸甜"),
                List.of("国民家常"),
                List.of("番茄", "鸡蛋")
        );

        when(markdownParser.checksum("meal/catalog/chinese-home-menu-v1.md")).thenReturn("checksum-b");
        when(datasetRepository.findByVersion("cn-home-menu-v1")).thenReturn(Optional.empty());
        when(markdownParser.parse("meal/catalog/chinese-home-menu-v1.md")).thenReturn(List.of(parsedItem));
        when(datasetRepository.save(any(MealCatalogDataset.class))).thenAnswer(invocation -> {
            MealCatalogDataset dataset = invocation.getArgument(0);
            dataset.setId(7L);
            return dataset;
        });
        when(tagRepository.findByTagTypeAndTagKey(any(), any())).thenReturn(Optional.empty());
        when(tagRepository.save(any(MealCatalogTag.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(itemRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(datasetRepository.findAll()).thenReturn(List.of());

        MealCatalogDataset result = mealCatalogService.ensureCatalogSeeded();

        assertEquals(7L, result.getId());
        ArgumentCaptor<List<MealCatalogItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(itemRepository).saveAll(captor.capture());
        MealCatalogItem savedItem = captor.getValue().get(0);
        assertEquals("cn-home-menu-v1", savedItem.getDatasetVersion());
        assertEquals("番茄炒蛋-051", savedItem.getSlug());
        assertEquals(7, savedItem.getItemTags().size());
    }

    @Test
    @DisplayName("getCatalog should return flattened feature and flavor tags")
    void getCatalog_ShouldReturnFlattenedTags() {
        MealCatalogDataset dataset = new MealCatalogDataset();
        dataset.setId(7L);
        dataset.setVersion("cn-home-menu-v1");
        dataset.setImportedAt(LocalDateTime.now());
        dataset.setActive(true);

        MealCatalogItem item = new MealCatalogItem();
        item.setId(11L);
        item.setDataset(dataset);
        item.setDatasetVersion("cn-home-menu-v1");
        item.setCode("cn-home-051");
        item.setSlug("番茄炒蛋-051");
        item.setName("番茄炒蛋");
        item.setCategory("蛋豆");
        item.setSubcategory("鸡蛋");
        item.setCookingMethod("炒");
        item.setRawFlavorText("酸甜、国民家常");
        item.setSourceIndex(51);

        item.getItemTags().add(createRelation(item, "CATEGORY", "蛋豆"));
        item.getItemTags().add(createRelation(item, "SUBCATEGORY", "鸡蛋"));
        item.getItemTags().add(createRelation(item, "COOKING_METHOD", "炒"));
        item.getItemTags().add(createRelation(item, "FLAVOR", "酸甜"));
        item.getItemTags().add(createRelation(item, "FEATURE", "国民家常"));
        item.getItemTags().add(createRelation(item, "INGREDIENT", "番茄"));
        item.getItemTags().add(createRelation(item, "INGREDIENT", "鸡蛋"));

        when(datasetRepository.findFirstByActiveTrueOrderByImportedAtDesc()).thenReturn(Optional.of(dataset));
        when(itemRepository.countByDatasetId(7L)).thenReturn(1L);
        when(itemRepository.findAllByDatasetVersionAndEnabledTrueOrderBySourceIndexAsc("cn-home-menu-v1"))
                .thenReturn(List.of(item));

        MealCatalogResponseDTO response = mealCatalogService.getCatalog();

        assertEquals("cn-home-menu-v1", response.getDatasetVersion());
        assertEquals(1, response.getTotal());
        MealCatalogItemDTO dto = response.getItems().get(0);
        assertEquals("番茄炒蛋", dto.getName());
        assertTrue(dto.getFlavorTags().contains("酸甜"));
        assertTrue(dto.getFeatureTags().contains("蛋豆"));
        assertTrue(dto.getFeatureTags().contains("鸡蛋"));
        assertTrue(dto.getFeatureTags().contains("炒"));
        assertTrue(dto.getFeatureTags().contains("番茄"));
        assertTrue(dto.getFeatureTags().contains("国民家常"));
    }

    private MealCatalogItemTag createRelation(MealCatalogItem item, String type, String label) {
        MealCatalogTag tag = new MealCatalogTag();
        tag.setTagType(type);
        tag.setTagKey(label);
        tag.setTagLabel(label);

        MealCatalogItemTag relation = new MealCatalogItemTag();
        relation.setItem(item);
        relation.setTag(tag);
        return relation;
    }
}
