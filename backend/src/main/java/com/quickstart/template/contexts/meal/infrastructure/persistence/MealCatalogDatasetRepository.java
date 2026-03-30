package com.quickstart.template.contexts.meal.infrastructure.persistence;

import com.quickstart.template.contexts.meal.domain.MealCatalogDataset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MealCatalogDatasetRepository extends JpaRepository<MealCatalogDataset, Long> {
    Optional<MealCatalogDataset> findByVersion(String version);

    Optional<MealCatalogDataset> findFirstByActiveTrueOrderByImportedAtDesc();
}
