package com.xx.aitranslation.service.chunk.strategy;

import com.xx.aitranslation.service.chunk.config.ChunkConfig;
import com.xx.aitranslation.service.chunk.config.ChunkStrategyType;
import com.xx.aitranslation.service.chunk.model.DocumentChunk;
import com.xx.aitranslation.service.chunk.support.ChunkFactorySupport;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown 标题拆分：以 ATX 标题（{@code #} ~ {@code ######}）为边界，每个标题及其正文构成一个块。
 * <p>
 * 块元数据写入 {@code title}（标题文本）与 {@code headingLevel}（标题层级）。
 * 首个标题之前的导言内容单独成块。
 */
@Component
public class MarkdownChunkStrategy implements ChunkStrategy {

    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.*)$");

    private final ChunkFactorySupport chunkFactorySupport;

    public MarkdownChunkStrategy(ChunkFactorySupport chunkFactorySupport) {
        this.chunkFactorySupport = chunkFactorySupport;
    }

    @Override
    public List<DocumentChunk> chunk(String content, ChunkConfig config) {
        List<DocumentChunk> chunks = new ArrayList<>();
        if (ObjectUtils.isEmpty(content)) {
            return chunks;
        }
        String[] lines = content.split("\n", -1);
        StringBuilder buffer = new StringBuilder();
        String currentTitle = null;
        int currentLevel = 0;
        int index = 0;

        for (String line : lines) {
            Matcher matcher = HEADING.matcher(line);
            if (matcher.matches()) {
                if (buffer.length() > 0) {
                    chunks.add(buildSection(buffer.toString(), currentTitle, currentLevel, index++));
                    buffer.setLength(0);
                }
                currentLevel = matcher.group(1).length();
                currentTitle = matcher.group(2).strip();
            }
            buffer.append(line).append('\n');
        }
        if (buffer.length() > 0) {
            chunks.add(buildSection(buffer.toString(), currentTitle, currentLevel, index));
        }
        return chunks;
    }

    private DocumentChunk buildSection(String text, String title, int headingLevel, int index) {
        Map<String, Object> metadata = new HashMap<>();
        if (!ObjectUtils.isEmpty(title)) {
            metadata.put("title", title);
        }
        metadata.put("headingLevel", headingLevel);
        return chunkFactorySupport.build(text.strip(), null, 1, index, metadata);
    }

    @Override
    public ChunkStrategyType type() {
        return ChunkStrategyType.MARKDOWN;
    }
}
