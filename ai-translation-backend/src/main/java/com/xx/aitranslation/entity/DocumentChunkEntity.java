package com.xx.aitranslation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档拆分结果持久化实体，对应表 {@code document_chunk}。
 * <p>
 * 独立于翻译解析相关表（translation_paragraph / translation_sentence），供拆分引擎单独存储复用。
 */
@Data
@TableName("document_chunk")
public class DocumentChunkEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 来源标识（由调用方指定，如任务ID / 业务文档ID），可为空。 */
    private Long sourceId;

    /** 源文件名。 */
    private String sourceName;

    /** 文件类型（ChunkFileType.name()）。 */
    private String fileType;

    /** 采用的拆分策略（ChunkStrategyType.name()）。 */
    private String strategy;

    /** 文档内块唯一标识（如 L1-0）。 */
    private String chunkKey;

    /** 父块标识，根级为空。 */
    private String parentKey;

    /** 层级：0=Root，1=Parent，2=Child。 */
    private Integer level;

    /** 同层级顺序号。 */
    private Integer orderNo;

    /** 块标题（Markdown 等策略可用）。 */
    private String title;

    /** 块内容。 */
    private String content;

    /** 估算 token 数。 */
    private Integer tokenCount;

    /** 字符数。 */
    private Integer charCount;

    private LocalDateTime createTime;
}
