package com.quickstart.template.contexts.community.infrastructure.search;

import com.quickstart.template.contexts.community.api.dto.PostDTO;
import com.quickstart.template.contexts.community.domain.Post;
import com.quickstart.template.contexts.community.infrastructure.persistence.PostRepository;
import com.quickstart.template.contexts.community.application.PostService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.search.provider", havingValue = "database", matchIfMissing = true)
public class DatabasePostSearchProvider implements PostSearchProvider {
    private final PostRepository postRepository;

    public DatabasePostSearchProvider(PostRepository postRepository) {
        this.postRepository = postRepository;
    }

    @Override
    public Page<PostDTO> search(String keyword, Pageable pageable) {
        return postRepository.searchByKeyword(keyword, pageable)
                .map(PostService::convertToDTOStatic);
    }

    @Override
    public void indexPost(Post post) {
        // Database search does not require a secondary index.
    }

    @Override
    public void deletePost(Long postId) {
        // Database search does not require a secondary index.
    }
}
