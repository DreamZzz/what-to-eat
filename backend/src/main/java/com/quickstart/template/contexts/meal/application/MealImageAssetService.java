package com.quickstart.template.contexts.meal.application;

import com.quickstart.template.contexts.meal.domain.MealImageAsset;
import com.quickstart.template.contexts.meal.infrastructure.persistence.MealImageAssetRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;

@Service
public class MealImageAssetService {
    private final MealImageAssetRepository mealImageAssetRepository;

    public MealImageAssetService(MealImageAssetRepository mealImageAssetRepository) {
        this.mealImageAssetRepository = mealImageAssetRepository;
    }

    public Optional<MealImageAsset> findLatestByDishName(String dishName) {
        String normalizedDishName = normalizeDishName(dishName);
        if (normalizedDishName.isBlank()) {
            return Optional.empty();
        }
        return mealImageAssetRepository.findTopByNormalizedDishNameOrderByUpdatedAtDescIdDesc(normalizedDishName);
    }

    @Transactional
    public MealImageAsset saveOrGetExisting(
            String dishName,
            String sourceImageUrl,
            String sourcePageUrl,
            String storageKey,
            String publicImageUrl,
            String sourceProvider
    ) {
        String normalizedDishName = normalizeDishName(dishName);
        Optional<MealImageAsset> existing = mealImageAssetRepository.findByNormalizedDishNameAndSourceImageUrl(
                normalizedDishName,
                sourceImageUrl
        );
        if (existing.isPresent()) {
            return existing.get();
        }

        MealImageAsset asset = new MealImageAsset();
        asset.setDishName(dishName);
        asset.setNormalizedDishName(normalizedDishName);
        asset.setSourceImageUrl(sourceImageUrl);
        asset.setSourcePageUrl(sourcePageUrl);
        asset.setStorageKey(storageKey);
        asset.setPublicImageUrl(publicImageUrl);
        asset.setSourceProvider(sourceProvider);

        try {
            return mealImageAssetRepository.save(asset);
        } catch (DataIntegrityViolationException exception) {
            return mealImageAssetRepository.findByNormalizedDishNameAndSourceImageUrl(normalizedDishName, sourceImageUrl)
                    .orElseThrow(() -> exception);
        }
    }

    public String normalizeDishName(String dishName) {
        if (dishName == null || dishName.isBlank()) {
            return "";
        }

        return dishName.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{IsHan}\\p{IsAlphabetic}\\p{IsDigit}]+", "");
    }
}
