package com.quickstart.template.shared.dto;

public class RetrievalMeta {
    private String scene;
    private String keyword;
    private String sortStrategy;
    private String provider;

    public RetrievalMeta() {
    }

    public RetrievalMeta(String scene, String keyword, String sortStrategy, String provider) {
        this.scene = scene;
        this.keyword = keyword;
        this.sortStrategy = sortStrategy;
        this.provider = provider;
    }

    public String getScene() {
        return scene;
    }

    public void setScene(String scene) {
        this.scene = scene;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public String getSortStrategy() {
        return sortStrategy;
    }

    public void setSortStrategy(String sortStrategy) {
        this.sortStrategy = sortStrategy;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }
}
