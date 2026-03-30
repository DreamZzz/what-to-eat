package com.quickstart.template.contexts.meal.api.dto;

import java.util.List;

public class MealCatalogResponseDTO {
    private String datasetVersion;
    private Integer total;
    private List<MealCatalogItemDTO> items;

    public String getDatasetVersion() {
        return datasetVersion;
    }

    public void setDatasetVersion(String datasetVersion) {
        this.datasetVersion = datasetVersion;
    }

    public Integer getTotal() {
        return total;
    }

    public void setTotal(Integer total) {
        this.total = total;
    }

    public List<MealCatalogItemDTO> getItems() {
        return items;
    }

    public void setItems(List<MealCatalogItemDTO> items) {
        this.items = items;
    }
}
