package com.quickstart.template.contexts.meal.infrastructure.persistence;

import com.quickstart.template.contexts.meal.domain.MealCatalogTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MealCatalogTagRepository extends JpaRepository<MealCatalogTag, Long> {
    Optional<MealCatalogTag> findByTagTypeAndTagKey(String tagType, String tagKey);
}
