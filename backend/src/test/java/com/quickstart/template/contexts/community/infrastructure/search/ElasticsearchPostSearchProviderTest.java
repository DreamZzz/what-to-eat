package com.quickstart.template.contexts.community.infrastructure.search;

import com.quickstart.template.contexts.community.api.dto.PostDTO;
import com.quickstart.template.contexts.community.domain.Post;
import com.quickstart.template.contexts.account.domain.User;
import com.quickstart.template.contexts.community.infrastructure.persistence.PostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ElasticsearchPostSearchProviderTest {

    @Mock
    private ElasticsearchOperations elasticsearchOperations;

    @Mock
    private PostRepository postRepository;

    @InjectMocks
    private ElasticsearchPostSearchProvider elasticsearchPostSearchProvider;

    private Post post;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setId(1L);
        user.setUsername("zhao");
        user.setDisplayName("赵强");

        post = new Post();
        post.setId(10L);
        post.setContent("三里屯探店");
        post.setLocationName("三里屯太古里");
        post.setLocationAddress("北京市 朝阳区");
        post.setUser(user);
    }

    @Test
    @DisplayName("search should fall back to database results when Elasticsearch search fails")
    void search_ShouldFallbackToDatabase_WhenElasticsearchFails() {
        Pageable pageable = PageRequest.of(0, 10);
        when(postRepository.searchByKeyword("三里屯", pageable))
                .thenReturn(new PageImpl<>(List.of(post), pageable, 1));
        when(elasticsearchOperations.search(any(NativeQuery.class), eq(PostSearchDocument.class)))
                .thenThrow(new RuntimeException("es down"));

        var result = elasticsearchPostSearchProvider.search("三里屯", pageable);

        assertEquals(1, result.getTotalElements());
        PostDTO dto = result.getContent().get(0);
        assertEquals("三里屯探店", dto.getContent());
        assertEquals("zhao", dto.getUsername());
        verify(postRepository).searchByKeyword("三里屯", pageable);
    }

    @Test
    @DisplayName("toDocument should include displayName for Elasticsearch indexing")
    void toDocument_ShouldIncludeDisplayName() {
        PostSearchDocument document = elasticsearchPostSearchProvider.toDocument(post);

        assertEquals(10L, document.getId());
        assertEquals("zhao", document.getUsername());
        assertEquals("赵强", document.getDisplayName());
        assertEquals("三里屯太古里", document.getLocationName());
        assertEquals("北京市 朝阳区", document.getLocationAddress());
    }

    @Test
    @DisplayName("index and delete should not throw when Elasticsearch operations fail")
    void indexAndDelete_ShouldNotThrow_WhenElasticsearchFails() {
        doThrow(new RuntimeException("es save failed"))
                .when(elasticsearchOperations).save(any(PostSearchDocument.class));
        doThrow(new RuntimeException("es delete failed"))
                .when(elasticsearchOperations).delete("10", PostSearchDocument.class);

        assertDoesNotThrow(() -> elasticsearchPostSearchProvider.indexPost(post));
        assertDoesNotThrow(() -> elasticsearchPostSearchProvider.deletePost(10L));

        verify(elasticsearchOperations).save(any(PostSearchDocument.class));
        verify(elasticsearchOperations).delete("10", PostSearchDocument.class);
    }
}
