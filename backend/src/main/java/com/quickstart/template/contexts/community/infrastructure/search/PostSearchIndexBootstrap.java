package com.quickstart.template.contexts.community.infrastructure.search;

import com.quickstart.template.contexts.community.domain.Post;
import com.quickstart.template.contexts.community.infrastructure.persistence.PostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnBean(ElasticsearchPostSearchProvider.class)
@ConditionalOnProperty(name = "app.search.elasticsearch.reindex-on-startup", havingValue = "true", matchIfMissing = true)
public class PostSearchIndexBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(PostSearchIndexBootstrap.class);

    private final PostRepository postRepository;
    private final ElasticsearchPostSearchProvider elasticsearchPostSearchProvider;

    public PostSearchIndexBootstrap(
            PostRepository postRepository,
            ElasticsearchPostSearchProvider elasticsearchPostSearchProvider) {
        this.postRepository = postRepository;
        this.elasticsearchPostSearchProvider = elasticsearchPostSearchProvider;
    }

    @Override
    @Transactional(readOnly = true)
    public void run(ApplicationArguments args) {
        // 启动时全量重建一次索引，保证本地开发和首次部署后搜索立即可用。
        long indexedCount = 0L;
        for (Post post : postRepository.findAll()) {
            elasticsearchPostSearchProvider.indexPost(post);
            indexedCount++;
        }
        log.info("Elasticsearch post index bootstrap completed, indexed {} posts", indexedCount);
    }
}
