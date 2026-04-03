package com.quickstart.template.contexts.meal.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.quickstart.template.contexts.account.domain.User;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "meal_recipes",
    indexes = @Index(name = "idx_meal_recipes_norm_src", columnList = "normalized_source_text")
)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class MealRecipe {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "meal_recipe_seq")
    @SequenceGenerator(name = "meal_recipe_seq", sequenceName = "meal_recipes_id_seq", allocationSize = 50)
    private Long id;

    @Column(name = "request_id", nullable = false, length = 64)
    private String requestId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "catalog_item_id")
    private MealCatalogItem catalogItem;

    @Column(name = "catalog_item_code", length = 80)
    private String catalogItemCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "source_text", nullable = false, length = 1000)
    private String sourceText;

    // Pre-computed lower(trim(source_text)) stored alongside source_text so the cache
    // lookup query can use an index instead of a full table scan.
    @Column(name = "normalized_source_text", nullable = false, length = 1000,
            columnDefinition = "varchar(1000) default ''")
    private String normalizedSourceText;

    @Column(name = "source_mode", nullable = false, length = 20)
    private String sourceMode;

    @Column(name = "dish_count", nullable = false)
    private Integer dishCount;

    @Column(name = "total_calories")
    private Integer totalCalories;

    @Column(name = "staple", length = 40)
    private String staple;

    @Column(name = "locale", length = 20)
    private String locale;

    @Column(name = "provider", nullable = false, length = 80)
    private String provider;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "summary", length = 1000)
    private String summary;

    @Column(name = "estimated_calories")
    private Integer estimatedCalories;

    @Column(name = "ingredients_json", columnDefinition = "TEXT")
    private String ingredientsJson;

    @Column(name = "seasonings_json", columnDefinition = "TEXT")
    private String seasoningsJson;

    @Column(name = "steps_json", columnDefinition = "TEXT")
    private String stepsJson;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "image_status", nullable = false, length = 20)
    private String imageStatus = "OMITTED";

    @Column(name = "steps_status", nullable = false, length = 20,
            columnDefinition = "varchar(20) default 'OMITTED'")
    private String stepsStatus = "OMITTED";

    @Column(name = "preference", length = 20)
    private String preference;

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

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public MealCatalogItem getCatalogItem() {
        return catalogItem;
    }

    public void setCatalogItem(MealCatalogItem catalogItem) {
        this.catalogItem = catalogItem;
    }

    public String getCatalogItemCode() {
        return catalogItemCode;
    }

    public void setCatalogItemCode(String catalogItemCode) {
        this.catalogItemCode = catalogItemCode;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getSourceText() {
        return sourceText;
    }

    public void setSourceText(String sourceText) {
        this.sourceText = sourceText;
    }

    public String getNormalizedSourceText() {
        return normalizedSourceText;
    }

    public void setNormalizedSourceText(String normalizedSourceText) {
        this.normalizedSourceText = normalizedSourceText;
    }

    public String getSourceMode() {
        return sourceMode;
    }

    public void setSourceMode(String sourceMode) {
        this.sourceMode = sourceMode;
    }

    public Integer getDishCount() {
        return dishCount;
    }

    public void setDishCount(Integer dishCount) {
        this.dishCount = dishCount;
    }

    public Integer getTotalCalories() {
        return totalCalories;
    }

    public void setTotalCalories(Integer totalCalories) {
        this.totalCalories = totalCalories;
    }

    public String getStaple() {
        return staple;
    }

    public void setStaple(String staple) {
        this.staple = staple;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public Integer getEstimatedCalories() {
        return estimatedCalories;
    }

    public void setEstimatedCalories(Integer estimatedCalories) {
        this.estimatedCalories = estimatedCalories;
    }

    public String getIngredientsJson() {
        return ingredientsJson;
    }

    public void setIngredientsJson(String ingredientsJson) {
        this.ingredientsJson = ingredientsJson;
    }

    public String getSeasoningsJson() {
        return seasoningsJson;
    }

    public void setSeasoningsJson(String seasoningsJson) {
        this.seasoningsJson = seasoningsJson;
    }

    public String getStepsJson() {
        return stepsJson;
    }

    public void setStepsJson(String stepsJson) {
        this.stepsJson = stepsJson;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getImageStatus() {
        return imageStatus;
    }

    public void setImageStatus(String imageStatus) {
        this.imageStatus = imageStatus;
    }

    public String getStepsStatus() {
        return stepsStatus;
    }

    public void setStepsStatus(String stepsStatus) {
        this.stepsStatus = stepsStatus;
    }

    public String getPreference() {
        return preference;
    }

    public void setPreference(String preference) {
        this.preference = preference;
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
