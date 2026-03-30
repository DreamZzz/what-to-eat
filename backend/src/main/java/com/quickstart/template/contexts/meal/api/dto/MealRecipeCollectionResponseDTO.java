package com.quickstart.template.contexts.meal.api.dto;

import com.quickstart.template.shared.dto.PaginationMeta;
import com.quickstart.template.shared.dto.RetrievalMeta;
import java.util.List;

public class MealRecipeCollectionResponseDTO {
    private List<RecipeDTO> items;
    private PaginationMeta pagination;
    private RetrievalMeta retrieval;

    public static MealRecipeCollectionResponseDTO fromPage(
            List<RecipeDTO> items,
            org.springframework.data.domain.Page<?> page,
            String scene,
            String keyword,
            String sortStrategy,
            String provider
    ) {
        MealRecipeCollectionResponseDTO response = new MealRecipeCollectionResponseDTO();
        response.setItems(items);
        response.setPagination(new PaginationMeta(
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext(),
                page.hasPrevious()
        ));
        response.setRetrieval(new RetrievalMeta(scene, keyword, sortStrategy, provider));
        return response;
    }

    public List<RecipeDTO> getItems() {
        return items;
    }

    public void setItems(List<RecipeDTO> items) {
        this.items = items;
    }

    public PaginationMeta getPagination() {
        return pagination;
    }

    public void setPagination(PaginationMeta pagination) {
        this.pagination = pagination;
    }

    public RetrievalMeta getRetrieval() {
        return retrieval;
    }

    public void setRetrieval(RetrievalMeta retrieval) {
        this.retrieval = retrieval;
    }
}
