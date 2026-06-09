package com.xx.aitranslation.service.chunk.strategy;

import com.xx.aitranslation.service.chunk.config.ChunkConfig;
import com.xx.aitranslation.service.chunk.config.ChunkStrategyType;
import com.xx.aitranslation.service.chunk.model.DocumentChunk;
import com.xx.aitranslation.service.chunk.support.ChunkFactorySupport;
import com.xx.aitranslation.service.chunk.support.SectionPartSplitter;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown 标题拆分：按可配置的最大标题深度切块，超长章节内再切 Part。
 * <p>
 * 块正文不含边界 ATX 标题行；标题写入 metadata。同一章节 Part 共享 {@code sectionId}。
 */
@Component
public class MarkdownChunkStrategy implements ChunkStrategy {

    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.*)$");
    private static final String PREFACE_SECTION_ID = "MD-0";

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

        int splitLevel = resolveSplitLevel(config);
        int maxSize = resolveSectionMaxSize(config);
        int[] index = {0};
        int[] sectionSeq = {0};

        StringBuilder buffer = new StringBuilder();
        String sectionId = PREFACE_SECTION_ID;
        String sectionTitle = null;
        int sectionLevel = 0;
        boolean sectionStarted = false;

        for (String line : content.split("\n", -1)) {
            Matcher matcher = HEADING.matcher(line);
            if (matcher.matches()) {
                int level = matcher.group(1).length();
                if (level <= splitLevel) {
                    flushSection(chunks, index, maxSize, sectionId, sectionTitle, sectionLevel, buffer);
                    buffer.setLength(0);
                    sectionSeq[0]++;
                    sectionId = "MD-" + sectionSeq[0];
                    sectionTitle = matcher.group(2).strip();
                    sectionLevel = level;
                    sectionStarted = true;
                    continue;
                }
            }
            if (!sectionStarted) {
                sectionStarted = true;
            }
            buffer.append(line).append('\n');
        }
        flushSection(chunks, index, maxSize, sectionId, sectionTitle, sectionLevel, buffer);
        return chunks;
    }

    private void flushSection(List<DocumentChunk> chunks, int[] index, int maxSize,
                              String sectionId, String sectionTitle, int sectionLevel,
                              StringBuilder buffer) {
        String body = buffer.toString().strip();
        if (ObjectUtils.isEmpty(body) && ObjectUtils.isEmpty(sectionTitle)) {
            return;
        }
        int level = sectionLevel > 0 ? sectionLevel : 1;
        if (ObjectUtils.isEmpty(body)) {
            chunks.add(buildChunk(sectionId, sectionTitle, sectionTitle, sectionId, level, index[0]++,
                    sectionTitle, null));
            return;
        }
        if (body.length() <= maxSize) {
            chunks.add(buildChunk(sectionId, body, sectionTitle, sectionId, level, index[0]++,
                    sectionTitle, null));
            return;
        }
        List<String> parts = SectionPartSplitter.splitContent(body, maxSize);
        int partNo = 1;
        for (String part : parts) {
            String partTitle = ObjectUtils.isEmpty(sectionTitle) ? "Part" + partNo : sectionTitle + "-Part" + partNo;
            chunks.add(buildChunk(sectionId + "-P" + partNo, part, sectionTitle, sectionId, level, index[0]++,
                    partTitle, partNo));
            partNo++;
        }
    }

    private DocumentChunk buildChunk(String id, String content, String sectionTitle, String sectionId,
                                     int level, int index, String displayTitle, Integer partIndex) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("sectionId", sectionId);
        metadata.put("sectionTitle", sectionTitle);
        metadata.put("headingLevel", level);
        if (!ObjectUtils.isEmpty(displayTitle)) {
            metadata.put("title", displayTitle);
        }
        if (partIndex != null) {
            metadata.put("partIndex", partIndex);
        }
        return chunkFactorySupport.buildWithId(id, content, null, level, index, metadata);
    }

    private static int resolveSplitLevel(ChunkConfig config) {
        if (config != null && config.getHeadingSplitLevel() > 0) {
            return Math.min(6, config.getHeadingSplitLevel());
        }
        return 1;
    }

    private static int resolveSectionMaxSize(ChunkConfig config) {
        if (config != null && config.getChunkSize() > 0) {
            return config.getChunkSize();
        }
        return TitleChunkStrategy.DEFAULT_SECTION_MAX_SIZE;
    }

    @Override
    public ChunkStrategyType type() {
        return ChunkStrategyType.MARKDOWN;
    }
}
