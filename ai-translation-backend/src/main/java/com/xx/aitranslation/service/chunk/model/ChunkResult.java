package com.xx.aitranslation.service.chunk.model;

import com.xx.aitranslation.service.chunk.config.ChunkFileType;
import com.xx.aitranslation.service.chunk.config.ChunkStrategyType;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 拆分引擎对外统一返回结果。
 */
@Data
@Builder
public class ChunkResult {

    /** 原始文件名。 */
    private String fileName;

    /** 文件类型。 */
    private ChunkFileType fileType;

    /** 实际采用的拆分策略。 */
    private ChunkStrategyType strategy;

    /** 扁平后的全部块（含层级信息）。 */
    private List<DocumentChunk> chunks;

    /** 层级拆分时的树结构，非层级策略为 {@code null}。 */
    private ChunkTree tree;

    /** 块总数。 */
    public int chunkCount() {
        return chunks == null ? 0 : chunks.size();
    }
}
