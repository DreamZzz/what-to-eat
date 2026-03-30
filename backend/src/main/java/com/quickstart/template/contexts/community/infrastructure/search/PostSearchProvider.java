package com.quickstart.template.contexts.community.infrastructure.search;

import com.quickstart.template.contexts.community.api.dto.PostDTO;
import com.quickstart.template.contexts.community.domain.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PostSearchProvider {
    Page<PostDTO> search(String keyword, Pageable pageable);

    void indexPost(Post post);

    void deletePost(Long postId);
}
