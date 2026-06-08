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
 * 句子拆分：以中英文句末标点为边界切分，每个句子独立成块。
 * <p>
 * 不受 {@code chunkSize} 限制，不做按字符数的合并。
 */
@Component
public class SentenceChunkStrategy implements ChunkStrategy {

    private final TextSplitSupport textSplitSupport;
    private final ChunkFactorySupport chunkFactorySupport;

    public SentenceChunkStrategy(TextSplitSupport textSplitSupport, ChunkFactorySupport chunkFactorySupport) {
        this.textSplitSupport = textSplitSupport;
        this.chunkFactorySupport = chunkFactorySupport;
    }

    @Override
    public List<DocumentChunk> chunk(String content, ChunkConfig config) {
        List<String> sentences = textSplitSupport.bySentence(content);
        List<DocumentChunk> chunks = new ArrayList<>(sentences.size());
        for (int i = 0; i < sentences.size(); i++) {
            chunks.add(chunkFactorySupport.buildFlat(sentences.get(i), i));
        }
        return chunks;
    }

    @Override
    public ChunkStrategyType type() {
        return ChunkStrategyType.SENTENCE;
    }
}
