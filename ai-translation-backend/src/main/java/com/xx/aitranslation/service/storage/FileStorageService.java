package com.xx.aitranslation.service.storage;

import java.io.InputStream;

/**
 * 文件存储抽象：屏蔽本地磁盘与 MinIO 等对象存储的差异。
 * <p>
 * 上传返回对象 key，后续以 key 进行下载；具体实现由 {@code app.storage.type} 决定。
 */
public interface FileStorageService {

    /**
     * 上传文件。
     *
     * @param in           文件输入流
     * @param originalName 原始文件名（用于推断扩展名）
     * @param contentType  内容类型（MIME）
     * @return 对象存储 key
     */
    String upload(InputStream in, String originalName, String contentType);

    /**
     * 按 key 下载文件。
     *
     * @param key 对象存储 key
     * @return 文件输入流
     */
    InputStream download(String key);
}
