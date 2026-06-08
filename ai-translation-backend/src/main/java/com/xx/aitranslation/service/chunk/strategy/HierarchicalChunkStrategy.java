package com.xx.aitranslation.service.chunk.strategy;

import com.xx.aitranslation.service.chunk.config.ChunkConfig;
import com.xx.aitranslation.service.chunk.model.ChunkTree;

/**
 * 层级拆分策略：将文本拆分为 Root → Parent → Child 的树结构，适合超大文件。
 */
public interface HierarchicalChunkStrategy {

    /**
     * 层级拆分。
     *
     * @param content 清洗后的文本
     * @param config  拆分配置（使用 parentSize / childSize）
     * @return 块树
     */
    ChunkTree chunk(String content, ChunkConfig config);
}
