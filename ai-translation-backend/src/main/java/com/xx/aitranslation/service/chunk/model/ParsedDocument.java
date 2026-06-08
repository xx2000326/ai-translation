package com.xx.aitranslation.service.chunk.model;

import com.xx.aitranslation.service.chunk.config.ChunkFileType;
import lombok.Builder;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * Reader 层输出：原始文档读取并归一化后的纯文本及元数据。
 */
@Data
@Builder
public class ParsedDocument {

    /** 原始文件名。 */
    private String fileName;

    /** 文件类型。 */
    private ChunkFileType fileType;

    /** 抽取出的纯文本内容（未清洗）。 */
    private String content;

    /** 读取过程产生的元数据（页数、来源等）。 */
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();
}
