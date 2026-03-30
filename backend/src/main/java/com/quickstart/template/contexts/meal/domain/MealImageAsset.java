package com.quickstart.template.contexts.meal.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "meal_image_assets",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_meal_image_asset_dish_source", columnNames = {"normalized_dish_name", "source_image_url"})
        }
)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class MealImageAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dish_name", nullable = false, length = 200)
    private String dishName;

    @Column(name = "normalized_dish_name", nullable = false, length = 200)
    private String normalizedDishName;

    @Column(name = "source_image_url", nullable = false, length = 1000)
    private String sourceImageUrl;

    @Column(name = "source_page_url", length = 1000)
    private String sourcePageUrl;

    @Column(name = "storage_key", nullable = false, length = 255)
    private String storageKey;

    @Column(name = "public_image_url", nullable = false, length = 1000)
    private String publicImageUrl;

    @Column(name = "source_provider", nullable = false, length = 80)
    private String sourceProvider;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDishName() {
        return dishName;
    }

    public void setDishName(String dishName) {
        this.dishName = dishName;
    }

    public String getNormalizedDishName() {
        return normalizedDishName;
    }

    public void setNormalizedDishName(String normalizedDishName) {
        this.normalizedDishName = normalizedDishName;
    }

    public String getSourceImageUrl() {
        return sourceImageUrl;
    }

    public void setSourceImageUrl(String sourceImageUrl) {
        this.sourceImageUrl = sourceImageUrl;
    }

    public String getSourcePageUrl() {
        return sourcePageUrl;
    }

    public void setSourcePageUrl(String sourcePageUrl) {
        this.sourcePageUrl = sourcePageUrl;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public void setStorageKey(String storageKey) {
        this.storageKey = storageKey;
    }

    public String getPublicImageUrl() {
        return publicImageUrl;
    }

    public void setPublicImageUrl(String publicImageUrl) {
        this.publicImageUrl = publicImageUrl;
    }

    public String getSourceProvider() {
        return sourceProvider;
    }

    public void setSourceProvider(String sourceProvider) {
        this.sourceProvider = sourceProvider;
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
