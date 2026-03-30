package com.quickstart.template.platform.config;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OssConfig {

    @Value("${aliyun.oss.endpoint}")
    private String endpoint;

    @Value("${aliyun.oss.access-key-id}")
    private String accessKeyId;

    @Value("${aliyun.oss.access-key-secret}")
    private String accessKeySecret;

    @Value("${aliyun.oss.bucket-name}")
    private String bucketName;

    @Value("${aliyun.oss.cdn-domain:}")
    private String cdnDomain;

    @Value("${aliyun.oss.folder:uploads/}")
    private String folder;

    @Bean
    @ConditionalOnProperty(name = "app.media.storage.provider", havingValue = "oss")
    @ConditionalOnExpression(
        "T(org.springframework.util.StringUtils).hasText('${aliyun.oss.access-key-id:}') " +
        "and T(org.springframework.util.StringUtils).hasText('${aliyun.oss.access-key-secret:}')"
    )
    public OSS ossClient() {
        return new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
    }

    @Bean
    @ConditionalOnProperty(name = "app.media.storage.provider", havingValue = "oss")
    @ConditionalOnExpression(
        "T(org.springframework.util.StringUtils).hasText('${aliyun.oss.access-key-id:}') " +
        "and T(org.springframework.util.StringUtils).hasText('${aliyun.oss.access-key-secret:}')"
    )
    public OssProperties ossProperties() {
        return new OssProperties(endpoint, accessKeyId, accessKeySecret, bucketName, cdnDomain, folder);
    }

    public static class OssProperties {
        private final String endpoint;
        private final String accessKeyId;
        private final String accessKeySecret;
        private final String bucketName;
        private final String cdnDomain;
        private final String folder;

        public OssProperties(String endpoint, String accessKeyId, String accessKeySecret, 
                           String bucketName, String cdnDomain, String folder) {
            this.endpoint = endpoint;
            this.accessKeyId = accessKeyId;
            this.accessKeySecret = accessKeySecret;
            this.bucketName = bucketName;
            this.cdnDomain = cdnDomain;
            this.folder = folder;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public String getAccessKeyId() {
            return accessKeyId;
        }

        public String getAccessKeySecret() {
            return accessKeySecret;
        }

        public String getBucketName() {
            return bucketName;
        }

        public String getCdnDomain() {
            return cdnDomain;
        }

        public String getFolder() {
            return folder;
        }
    }
}
