package com.quickstart.template.contexts.meal.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "meal_catalog_items",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_meal_catalog_item_dataset_code", columnNames = {"dataset_id", "code"})
        }
)
public class MealCatalogItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dataset_id", nullable = false)
    private MealCatalogDataset dataset;

    @Column(name = "dataset_version", nullable = false, length = 80)
    private String datasetVersion;

    @Column(name = "source_index", nullable = false)
    private Integer sourceIndex;

    @Column(name = "code", nullable = false, length = 80)
    private String code;

    @Column(name = "slug", nullable = false, length = 120)
    private String slug;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "category", nullable = false, length = 80)
    private String category;

    @Column(name = "subcategory", nullable = false, length = 80)
    private String subcategory;

    @Column(name = "cooking_method", nullable = false, length = 80)
    private String cookingMethod;

    @Column(name = "raw_flavor_text", nullable = false, length = 200)
    private String rawFlavorText;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;

    @OneToMany(mappedBy = "item", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MealCatalogItemTag> itemTags = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public void addTag(MealCatalogTag tag) {
        MealCatalogItemTag relation = new MealCatalogItemTag();
        relation.setItem(this);
        relation.setTag(tag);
        itemTags.add(relation);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public MealCatalogDataset getDataset() {
        return dataset;
    }

    public void setDataset(MealCatalogDataset dataset) {
        this.dataset = dataset;
    }

    public String getDatasetVersion() {
        return datasetVersion;
    }

    public void setDatasetVersion(String datasetVersion) {
        this.datasetVersion = datasetVersion;
    }

    public Integer getSourceIndex() {
        return sourceIndex;
    }

    public void setSourceIndex(Integer sourceIndex) {
        this.sourceIndex = sourceIndex;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getSubcategory() {
        return subcategory;
    }

    public void setSubcategory(String subcategory) {
        this.subcategory = subcategory;
    }

    public String getCookingMethod() {
        return cookingMethod;
    }

    public void setCookingMethod(String cookingMethod) {
        this.cookingMethod = cookingMethod;
    }

    public String getRawFlavorText() {
        return rawFlavorText;
    }

    public void setRawFlavorText(String rawFlavorText) {
        this.rawFlavorText = rawFlavorText;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public List<MealCatalogItemTag> getItemTags() {
        return itemTags;
    }

    public void setItemTags(List<MealCatalogItemTag> itemTags) {
        this.itemTags = itemTags;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
