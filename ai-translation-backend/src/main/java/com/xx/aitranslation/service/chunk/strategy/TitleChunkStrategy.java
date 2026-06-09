package com.xx.aitranslation.service.chunk.strategy;

import com.xx.aitranslation.service.chunk.config.ChunkConfig;
import com.xx.aitranslation.service.chunk.config.ChunkStrategyType;
import com.xx.aitranslation.service.chunk.model.DocumentChunk;
import com.xx.aitranslation.service.chunk.model.DocumentNode;
import com.xx.aitranslation.service.chunk.support.ChunkFactorySupport;
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
 * 适用于 Word / PDF / Markdown 等含标题层级的文档。每个标题节点产出一个块，块内容 = 标题 + 该标题自身正文（不含下级标题）。
 * 块通过 {@code level} 与 {@code parentId} 表达层级关系，未识别到任何标题时整篇作为单个块返回。
 */
@Component
public class TitleChunkStrategy implements ChunkStrategy {

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

        // 首个标题之前的正文（或无标题时的整篇内容），作为 level=0 的根块
        if (!ObjectUtils.isEmpty(root.getContent())) {
            chunks.add(chunkFactorySupport.buildWithId(root.getId(), root.getContent(), null, 0, index[0]++, null));
        }
        // 先序遍历：保证"一级 → 其下二级 → 其下三级 …… → 同级下一个"的层级顺序
        for (DocumentNode child : root.getChildren()) {
            appendNode(child, chunks, index);
        }
        return chunks;
    }

    private void appendNode(DocumentNode node, List<DocumentChunk> chunks, int[] index) {
        String text = ObjectUtils.isEmpty(node.getContent())
                ? node.getTitle()
                : node.getTitle() + "\n" + node.getContent();
        String parentId = (node.getParent() == null || node.getParent().getLevel() == 0)
                ? null
                : node.getParent().getId();

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("title", node.getTitle());
        metadata.put("headingLevel", node.getLevel());

        chunks.add(chunkFactorySupport.buildWithId(node.getId(), text, parentId, node.getLevel(), index[0]++, metadata));

        for (DocumentNode child : node.getChildren()) {
            appendNode(child, chunks, index);
        }
    }

    @Override
    public ChunkStrategyType type() {
        return ChunkStrategyType.TITLE;
    }
}
