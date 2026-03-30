package com.quickstart.template.contexts.meal.application;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.meal.catalog.bootstrap-enabled", havingValue = "true", matchIfMissing = true)
public class MealCatalogBootstrap implements ApplicationRunner {
    private final MealCatalogService mealCatalogService;

    public MealCatalogBootstrap(MealCatalogService mealCatalogService) {
        this.mealCatalogService = mealCatalogService;
    }

    @Override
    public void run(ApplicationArguments args) {
        mealCatalogService.ensureCatalogSeeded();
    }
}
