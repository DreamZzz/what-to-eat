package com.quickstart.template.contexts.meal.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "meal_catalog_datasets",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_meal_catalog_dataset_version", columnNames = "version")
        }
)
public class MealCatalogDataset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "version", nullable = false, length = 80)
    private String version;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "source_file", nullable = false, length = 200)
    private String sourceFile;

    @Column(name = "source_checksum", nullable = false, length = 64)
    private String sourceChecksum;

    @Column(name = "total_items", nullable = false)
    private Integer totalItems;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Column(name = "imported_at", nullable = false)
    private LocalDateTime importedAt;

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

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSourceFile() {
        return sourceFile;
    }

    public void setSourceFile(String sourceFile) {
        this.sourceFile = sourceFile;
    }

    public String getSourceChecksum() {
        return sourceChecksum;
    }

    public void setSourceChecksum(String sourceChecksum) {
        this.sourceChecksum = sourceChecksum;
    }

    public Integer getTotalItems() {
        return totalItems;
    }

    public void setTotalItems(Integer totalItems) {
        this.totalItems = totalItems;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public LocalDateTime getImportedAt() {
        return importedAt;
    }

    public void setImportedAt(LocalDateTime importedAt) {
        this.importedAt = importedAt;
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
