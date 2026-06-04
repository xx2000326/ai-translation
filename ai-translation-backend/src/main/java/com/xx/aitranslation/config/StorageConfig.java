package com.xx.aitranslation.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 对象存储配置：仅当 {@code app.storage.type=minio} 时创建 {@link MinioClient}。
 * <p>
 * Bean 初始化时幂等创建 bucket（不存在则建），避免上传时报桶不存在。
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "app.storage.type", havingValue = "minio")
public class StorageConfig {

    @Value("${app.storage.minio.endpoint}")
    private String endpoint;

    @Value("${app.storage.minio.access-key}")
    private String accessKey;

    @Value("${app.storage.minio.secret-key}")
    private String secretKey;

    @Value("${app.storage.minio.bucket:ai-translation}")
    private String bucket;

    @Bean
    public MinioClient minioClient() {
        MinioClient client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
        ensureBucket(client);
        return client;
    }

    private void ensureBucket(MinioClient client) {
        try {
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("MinIO bucket 创建成功: {}", bucket);
            }
        } catch (Exception e) {
            log.error("MinIO bucket 初始化失败: {}", e.getMessage(), e);
        }
    }
}
