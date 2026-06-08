package com.xx.aitranslation.service.chunk.strategy;

import com.xx.aitranslation.service.chunk.config.ChunkConfig;
import com.xx.aitranslation.service.chunk.config.ChunkStrategyType;
import com.xx.aitranslation.service.chunk.model.DocumentChunk;

import java.util.List;

/**
 * 扁平拆分策略：将清洗后的文本拆分为一组同级块（level = 1）。
 * <p>
 * 设计为无状态组件，配置通过 {@link ChunkConfig} 入参传入，保证可被并发复用。
 */
public interface ChunkStrategy {

    /**
     * 拆分文本。
     *
     * @param content 清洗后的文本
     * @param config  拆分配置
     * @return 块列表
     */
    List<DocumentChunk> chunk(String content, ChunkConfig config);

    /**
     * 当前策略类型。
     */
    ChunkStrategyType type();
}
