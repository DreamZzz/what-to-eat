package com.quickstart.template.contexts.meal.infrastructure.persistence;

import com.quickstart.template.contexts.meal.domain.MealImageAsset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MealImageAssetRepository extends JpaRepository<MealImageAsset, Long> {
    Optional<MealImageAsset> findTopByNormalizedDishNameOrderByUpdatedAtDescIdDesc(String normalizedDishName);

    Optional<MealImageAsset> findByNormalizedDishNameAndSourceImageUrl(String normalizedDishName, String sourceImageUrl);
}
