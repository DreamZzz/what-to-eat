package com.quickstart.template.platform.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchConfiguration;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.Arrays;

@org.springframework.context.annotation.Configuration
@ConditionalOnProperty(name = "app.search.provider", havingValue = "elasticsearch")
public class ElasticsearchConfig extends ElasticsearchConfiguration {

    @Value("${spring.elasticsearch.uris:http://localhost:9200}")
    private String uris;

    @Value("${spring.elasticsearch.username:}")
    private String username;

    @Value("${spring.elasticsearch.password:}")
    private String password;

    @Override
    public ClientConfiguration clientConfiguration() {
        if (StringUtils.hasText(username)) {
            return ClientConfiguration.builder()
                    .connectedTo(parseHosts(uris))
                    .withBasicAuth(username, password)
                    .build();
        }

        return ClientConfiguration.builder()
                .connectedTo(parseHosts(uris))
                .build();
    }

    private String[] parseHosts(String configuredUris) {
        return Arrays.stream(configuredUris.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(this::toHostAndPort)
                .toArray(String[]::new);
    }

    private String toHostAndPort(String rawUri) {
        if (!rawUri.contains("://")) {
            return rawUri;
        }
        URI uri = URI.create(rawUri);
        int port = uri.getPort();
        if (port < 0) {
            port = "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
        }
        return uri.getHost() + ":" + port;
    }
}
