package com.quickstart.template.contexts.community.api.dto;

import com.quickstart.template.shared.dto.PaginationMeta;
import com.quickstart.template.shared.dto.RetrievalMeta;
import org.springframework.data.domain.Page;

import java.util.List;

public class PostCollectionResponse {
    private List<PostDTO> items;
    private PaginationMeta pagination;
    private RetrievalMeta retrieval;

    public static PostCollectionResponse fromPage(
            Page<PostDTO> page,
            String scene,
            String keyword,
            String sortStrategy,
            String provider
    ) {
        PostCollectionResponse response = new PostCollectionResponse();
        response.setItems(page.getContent());
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

    public List<PostDTO> getItems() {
        return items;
    }

    public void setItems(List<PostDTO> items) {
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
