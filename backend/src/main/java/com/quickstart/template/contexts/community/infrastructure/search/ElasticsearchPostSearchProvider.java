package com.quickstart.template.contexts.community.infrastructure.search;

import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import com.quickstart.template.contexts.community.api.dto.PostDTO;
import com.quickstart.template.contexts.community.domain.Post;
import com.quickstart.template.contexts.community.infrastructure.persistence.PostRepository;
import com.quickstart.template.contexts.community.application.PostService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@ConditionalOnBean(ElasticsearchOperations.class)
@ConditionalOnProperty(name = "app.search.provider", havingValue = "elasticsearch")
public class ElasticsearchPostSearchProvider implements PostSearchProvider {
    private static final Logger log = LoggerFactory.getLogger(ElasticsearchPostSearchProvider.class);

    private final ElasticsearchOperations elasticsearchOperations;
    private final PostRepository postRepository;

    public ElasticsearchPostSearchProvider(ElasticsearchOperations elasticsearchOperations, PostRepository postRepository) {
        this.elasticsearchOperations = elasticsearchOperations;
        this.postRepository = postRepository;
    }

    @Override
    public Page<PostDTO> search(String keyword, Pageable pageable) {
        try {
            // ES负责召回和排序，随后再回数据库组装DTO，避免搜索索引和API返回结构强耦合。
            NativeQuery query = NativeQuery.builder()
                    .withPageable(pageable)
                    .withQuery(q -> q.multiMatch(mm -> mm
                            .query(keyword)
                            .fields("content", "username", "displayName", "locationName", "locationAddress")
                            .fuzziness("AUTO")
                            .operator(Operator.Or)))
                    .build();

            SearchHits<PostSearchDocument> hits = elasticsearchOperations.search(query, PostSearchDocument.class);
            List<Long> ids = hits.getSearchHits().stream()
                    .map(hit -> hit.getContent().getId())
                    .toList();

            if (ids.isEmpty()) {
                return Page.empty(pageable);
            }

            Map<Long, Post> postsById = postRepository.findAllById(ids).stream()
                    .collect(Collectors.toMap(Post::getId, post -> post, (left, right) -> left, LinkedHashMap::new));

            List<PostDTO> content = new ArrayList<>();
            for (Long id : ids) {
                Post post = postsById.get(id);
                if (post != null) {
                    content.add(PostService.convertToDTOStatic(post));
                }
            }

            return new PageImpl<>(content, pageable, hits.getTotalHits());
        } catch (RuntimeException exception) {
            log.warn("Elasticsearch search failed, falling back to database search", exception);
            return postRepository.searchByKeyword(keyword, pageable)
                    .map(PostService::convertToDTOStatic);
        }
    }

    @Override
    public void indexPost(Post post) {
        try {
            elasticsearchOperations.save(toDocument(post));
        } catch (RuntimeException exception) {
            log.warn("Failed to index post {} into Elasticsearch", post.getId(), exception);
        }
    }

    @Override
    public void deletePost(Long postId) {
        try {
            elasticsearchOperations.delete(String.valueOf(postId), PostSearchDocument.class);
        } catch (RuntimeException exception) {
            log.warn("Failed to delete post {} from Elasticsearch index", postId, exception);
        }
    }

    PostSearchDocument toDocument(Post post) {
        PostSearchDocument document = new PostSearchDocument();
        document.setId(post.getId());
        // 索引字段只保留搜索命中和列表展示需要的最小子集，避免把完整Post图结构复制进ES。
        document.setContent(post.getContent());
        document.setUsername(post.getUser().getUsername());
        document.setDisplayName(post.getUser().getDisplayName());
        document.setLocationName(post.getLocationName());
        document.setLocationAddress(post.getLocationAddress());
        document.setImageUrls(post.getImageUrls());
        document.setLikeCount(post.getLikeCount());
        document.setCommentCount(post.getCommentCount());
        document.setCreatedAt(post.getCreatedAt());
        return document;
    }
}
