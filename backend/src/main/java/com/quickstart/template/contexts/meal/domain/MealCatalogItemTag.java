package com.quickstart.template.contexts.meal.domain;

import jakarta.persistence.*;

@Entity
@Table(
        name = "meal_catalog_item_tags",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_meal_catalog_item_tag", columnNames = {"item_id", "tag_id"})
        }
)
public class MealCatalogItemTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private MealCatalogItem item;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tag_id", nullable = false)
    private MealCatalogTag tag;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public MealCatalogItem getItem() {
        return item;
    }

    public void setItem(MealCatalogItem item) {
        this.item = item;
    }

    public MealCatalogTag getTag() {
        return tag;
    }

    public void setTag(MealCatalogTag tag) {
        this.tag = tag;
    }
}
