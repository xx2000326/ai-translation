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
 * 段落拆分：严格以连续空行（{@code \n\n} 或连续空白行）为分隔符切分，每个段落独立成块。
 * <p>
 * 不受 {@code chunkSize} 限制，不做任何按字符数的合并或切割。
 */
@Component
public class ParagraphChunkStrategy implements ChunkStrategy {

    private final TextSplitSupport textSplitSupport;
    private final ChunkFactorySupport chunkFactorySupport;

    public ParagraphChunkStrategy(TextSplitSupport textSplitSupport, ChunkFactorySupport chunkFactorySupport) {
        this.textSplitSupport = textSplitSupport;
        this.chunkFactorySupport = chunkFactorySupport;
    }

    @Override
    public List<DocumentChunk> chunk(String content, ChunkConfig config) {
        List<String> paragraphs = textSplitSupport.byParagraph(content);
        List<DocumentChunk> chunks = new ArrayList<>(paragraphs.size());
        for (int i = 0; i < paragraphs.size(); i++) {
            chunks.add(chunkFactorySupport.buildFlat(paragraphs.get(i), i));
        }
        return chunks;
    }

    @Override
    public ChunkStrategyType type() {
        return ChunkStrategyType.PARAGRAPH;
    }
}
