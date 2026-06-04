package com.xx.aitranslation.service.storage;

import com.xx.aitranslation.common.BizException;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.io.InputStream;
import java.util.UUID;

/**
 * MinIO 对象存储实现：当 {@code app.storage.type=minio} 时启用。
 * <p>
 * 文件上传到 {@code app.storage.minio.bucket}，key 为 {@code UUID + 原始扩展名}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.storage.type", havingValue = "minio")
public class MinioFileStorageService implements FileStorageService {

    private final MinioClient minioClient;

    @Value("${app.storage.minio.bucket:ai-translation}")
    private String bucket;

    @Override
    public String upload(InputStream in, String originalName, String contentType) {
        try {
            String key = UUID.randomUUID() + extension(originalName);
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .stream(in, -1, 10485760)
                    .contentType(ObjectUtils.isEmpty(contentType) ? "application/octet-stream" : contentType)
                    .build());
            log.debug("MinIO 上传成功: bucket={}, key={}", bucket, key);
            return key;
        } catch (Exception e) {
            log.error("MinIO 上传失败: {}", e.getMessage(), e);
            throw new BizException("file.upload.failed");
        }
    }

    @Override
    public InputStream download(String key) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .build());
        } catch (Exception e) {
            log.error("MinIO 下载失败: {}", e.getMessage(), e);
            throw new BizException("file.download.failed");
        }
    }

    private String extension(String originalName) {
        if (ObjectUtils.isEmpty(originalName)) {
            return "";
        }
        int dot = originalName.lastIndexOf('.');
        return dot < 0 ? "" : originalName.substring(dot);
    }
}
