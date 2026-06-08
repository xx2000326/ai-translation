package com.xx.aitranslation.service.chunk.reader;

import com.xx.aitranslation.service.chunk.config.ChunkFileType;
import com.xx.aitranslation.service.chunk.model.ParsedDocument;
import org.springframework.core.io.Resource;

/**
 * 文档读取器：将源文件抽取为统一的纯文本 {@link ParsedDocument}。
 * <p>
 * 仅负责"读取 + 文本抽取"，不做清洗与拆分，便于按文件类型横向扩展。
 */
public interface DocumentReader {

    /**
     * 读取文档。
     *
     * @param resource 源文件资源
     * @param fileName 原始文件名
     * @return 抽取后的文档
     */
    ParsedDocument read(Resource resource, String fileName);

    /**
     * 当前读取器支持的文件类型。
     */
    ChunkFileType supportType();
}
