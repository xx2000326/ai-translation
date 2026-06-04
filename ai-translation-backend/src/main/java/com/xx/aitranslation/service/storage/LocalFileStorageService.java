package com.xx.aitranslation.service.storage;

import com.xx.aitranslation.common.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * 本地磁盘文件存储实现（默认）：当 {@code app.storage.type=local} 或未配置时启用。
 * <p>
 * 文件写入 {@code app.storage.local-dir} 目录，key 为 {@code UUID + 原始扩展名}。
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalFileStorageService implements FileStorageService {

    @Value("${app.storage.local-dir:./upload-files}")
    private String localDir;

    @Override
    public String upload(InputStream in, String originalName, String contentType) {
        try {
            Path dir = Paths.get(localDir);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
            String key = UUID.randomUUID() + extension(originalName);
            Path target = dir.resolve(key);
            Files.copy(in, target);
            log.debug("本地存储上传成功: {}", target.toAbsolutePath());
            return key;
        } catch (IOException e) {
            log.error("本地存储上传失败: {}", e.getMessage(), e);
            throw new BizException("file.upload.failed");
        }
    }

    @Override
    public InputStream download(String key) {
        try {
            Path target = Paths.get(localDir).resolve(key);
            return Files.newInputStream(target);
        } catch (IOException e) {
            log.error("本地存储下载失败: {}", e.getMessage(), e);
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
