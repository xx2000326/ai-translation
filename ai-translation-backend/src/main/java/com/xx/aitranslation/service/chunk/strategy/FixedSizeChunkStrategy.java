package com.xx.aitranslation.service.chunk.strategy;

import com.xx.aitranslation.service.chunk.config.ChunkConfig;
import com.xx.aitranslation.service.chunk.config.ChunkStrategyType;
import com.xx.aitranslation.service.chunk.model.DocumentChunk;
import com.xx.aitranslation.service.chunk.support.ChunkFactorySupport;
import com.xx.aitranslation.service.chunk.support.TextSplitSupport;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 固定长度拆分：按 {@link ChunkConfig#getChunkSize()} 字符切分，相邻块按 overlap 重叠。
 */
@Component
public class FixedSizeChunkStrategy implements ChunkStrategy {

    private final TextSplitSupport textSplitSupport;
    private final ChunkFactorySupport chunkFactorySupport;

    public FixedSizeChunkStrategy(TextSplitSupport textSplitSupport, ChunkFactorySupport chunkFactorySupport) {
        this.textSplitSupport = textSplitSupport;
        this.chunkFactorySupport = chunkFactorySupport;
    }

    @Override
    public List<DocumentChunk> chunk(String content, ChunkConfig config) {
        List<String> windows = textSplitSupport.fixedWindows(content, config.getChunkSize(), config.getOverlap());
        List<DocumentChunk> chunks = new ArrayList<>(windows.size());
        for (int i = 0; i < windows.size(); i++) {
            chunks.add(chunkFactorySupport.buildFlat(windows.get(i), i));
        }
        return chunks;
    }

    @Override
    public ChunkStrategyType type() {
        return ChunkStrategyType.FIXED_SIZE;
    }
}
