package com.quickstart.template.contexts.meal.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "meal_catalog_tags",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_meal_catalog_tag_type_key", columnNames = {"tag_type", "tag_key"})
        }
)
public class MealCatalogTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tag_type", nullable = false, length = 40)
    private String tagType;

    @Column(name = "tag_key", nullable = false, length = 120)
    private String tagKey;

    @Column(name = "tag_label", nullable = false, length = 120)
    private String tagLabel;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTagType() {
        return tagType;
    }

    public void setTagType(String tagType) {
        this.tagType = tagType;
    }

    public String getTagKey() {
        return tagKey;
    }

    public void setTagKey(String tagKey) {
        this.tagKey = tagKey;
    }

    public String getTagLabel() {
        return tagLabel;
    }

    public void setTagLabel(String tagLabel) {
        this.tagLabel = tagLabel;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
