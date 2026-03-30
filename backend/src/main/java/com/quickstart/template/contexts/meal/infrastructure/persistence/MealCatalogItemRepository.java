package com.quickstart.template.contexts.meal.infrastructure.persistence;

import com.quickstart.template.contexts.meal.domain.MealCatalogItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MealCatalogItemRepository extends JpaRepository<MealCatalogItem, Long> {
    long countByDatasetId(Long datasetId);

    long countByDatasetVersionAndEnabledTrue(String datasetVersion);

    @EntityGraph(attributePaths = {"dataset", "itemTags", "itemTags.tag"})
    List<MealCatalogItem> findAllByDatasetVersionAndEnabledTrueOrderBySourceIndexAsc(String datasetVersion);

    @EntityGraph(attributePaths = {"dataset", "itemTags", "itemTags.tag"})
    List<MealCatalogItem> findAllByDatasetIdAndEnabledTrueOrderBySourceIndexAsc(Long datasetId);

    @EntityGraph(attributePaths = {"dataset", "itemTags", "itemTags.tag"})
    java.util.Optional<MealCatalogItem> findByIdAndDatasetVersionAndEnabledTrue(Long id, String datasetVersion);
}
