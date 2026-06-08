package com.xx.aitranslation.service.chunk.model;

import lombok.Builder;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * 拆分结果的最小单元。
 * <p>
 * 通过 {@link #parentId} 与 {@link #level} 表达父子层级关系：
 * <ul>
 *     <li>level = 0：Root（整篇文档）</li>
 *     <li>level = 1：Parent Chunk</li>
 *     <li>level = 2：Child Chunk</li>
 * </ul>
 * 扁平策略产出的块统一为 level = 1、parentId = null。
 */
@Data
@Builder
public class DocumentChunk {

    /** 块唯一标识（文档内唯一）。 */
    private String id;

    /** 父块标识，根级为 {@code null}。 */
    private String parentId;

    /** 层级：0=Root，1=Parent，2=Child。 */
    private Integer level;

    /** 同层级内顺序号（从 0 开始）。 */
    private Integer index;

    /** 块文本内容。 */
    private String content;

    /** 估算 token 数。 */
    private Integer tokenCount;

    /** 字符数。 */
    private Integer charCount;

    /** 扩展元数据（标题、来源块范围等）。 */
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();
}
