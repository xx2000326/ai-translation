package com.xx.aitranslation.service.chunk.strategy;

import com.xx.aitranslation.service.chunk.config.ChunkConfig;
import com.xx.aitranslation.service.chunk.config.ChunkStrategyType;
import com.xx.aitranslation.service.chunk.model.DocumentChunk;
import com.xx.aitranslation.service.chunk.model.DocumentNode;
import com.xx.aitranslation.service.chunk.support.ChunkFactorySupport;
import com.xx.aitranslation.service.chunk.support.SectionPartSplitter;
import com.xx.aitranslation.service.chunk.support.TitleStructureSupport;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 标题结构拆分：按文档标题层级构建标题树，再按"一级标题 → 二级标题 → …… → 正文"的层级顺序输出块。
 * <p>
 * 单章节超过 {@link ChunkConfig#getChunkSize()} 时按段落切分为 {@code 标题-PartN}，同一章节 Part 共享 {@code sectionId}。
 * 块正文仅含章节正文，标题（如「第 X 章」）写入 metadata，不重复拼入正文。
 */
@Component
public class TitleChunkStrategy implements ChunkStrategy {

    /** TITLE / AUTO 未配置 chunk_size 时的章节块字符上限默认值。 */
    public static final int DEFAULT_SECTION_MAX_SIZE = 200;

    private final TitleStructureSupport titleStructureSupport;
    private final ChunkFactorySupport chunkFactorySupport;

    public TitleChunkStrategy(TitleStructureSupport titleStructureSupport, ChunkFactorySupport chunkFactorySupport) {
        this.titleStructureSupport = titleStructureSupport;
        this.chunkFactorySupport = chunkFactorySupport;
    }

    @Override
    public List<DocumentChunk> chunk(String content, ChunkConfig config) {
        DocumentNode root = titleStructureSupport.buildTree(content);
        List<DocumentChunk> chunks = new ArrayList<>();
        int[] index = {0};

        if (!ObjectUtils.isEmpty(root.getContent())) {
            emitNode(root, chunks, index, config);
        }
        for (DocumentNode child : root.getChildren()) {
            appendNode(child, chunks, index, config);
        }
        return chunks;
    }

    private void appendNode(DocumentNode node, List<DocumentChunk> chunks, int[] index, ChunkConfig config) {
        emitNode(node, chunks, index, config);
        for (DocumentNode child : node.getChildren()) {
            appendNode(child, chunks, index, config);
        }
    }

    private void emitNode(DocumentNode node, List<DocumentChunk> chunks, int[] index, ChunkConfig config) {
        String title = node.getTitle();
        String body = node.getContent();
        if (ObjectUtils.isEmpty(body) && ObjectUtils.isEmpty(title)) {
            return;
        }

        int maxSize = resolveSectionMaxSize(config);
        String parentId = resolveParentId(node);

        if (ObjectUtils.isEmpty(body)) {
            chunks.add(buildChunk(node, node.getId(), title, parentId, node.getLevel(), index[0]++,
                    title, null));
            return;
        }

        if (body.length() <= maxSize) {
            chunks.add(buildChunk(node, node.getId(), body, parentId, node.getLevel(), index[0]++,
                    title, null));
            return;
        }

        List<String> parts = SectionPartSplitter.splitContent(body, maxSize);
        int partNo = 1;
        for (String part : parts) {
            String partTitle = ObjectUtils.isEmpty(title) ? "Part" + partNo : title + "-Part" + partNo;
            String chunkId = node.getId() + "-P" + partNo;
            chunks.add(buildChunk(node, chunkId, part, parentId, node.getLevel(), index[0]++,
                    partTitle, partNo));
            partNo++;
        }
    }

    private DocumentChunk buildChunk(DocumentNode node, String id, String content, String parentId, int level,
                                     int index, String displayTitle, Integer partIndex) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("sectionId", node.getId());
        metadata.put("sectionTitle", node.getTitle());
        metadata.put("headingLevel", node.getLevel());
        if (!ObjectUtils.isEmpty(displayTitle)) {
            metadata.put("title", displayTitle);
        }
        if (partIndex != null) {
            metadata.put("partIndex", partIndex);
        }
        return chunkFactorySupport.buildWithId(id, content, parentId, level, index, metadata);
    }

    private static String resolveParentId(DocumentNode node) {
        if (node.getParent() == null || node.getParent().getLevel() == 0) {
            return null;
        }
        return node.getParent().getId();
    }

    private static int resolveSectionMaxSize(ChunkConfig config) {
        if (config != null && config.getChunkSize() > 0) {
            return config.getChunkSize();
        }
        return DEFAULT_SECTION_MAX_SIZE;
    }

    @Override
    public ChunkStrategyType type() {
        return ChunkStrategyType.TITLE;
    }
}
