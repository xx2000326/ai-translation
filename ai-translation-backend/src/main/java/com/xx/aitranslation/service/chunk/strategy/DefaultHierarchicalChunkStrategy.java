package com.xx.aitranslation.service.chunk.strategy;

import com.xx.aitranslation.service.chunk.config.ChunkConfig;
import com.xx.aitranslation.service.chunk.model.ChunkTree;
import com.xx.aitranslation.service.chunk.model.DocumentChunk;
import com.xx.aitranslation.service.chunk.support.ChunkFactorySupport;
import com.xx.aitranslation.service.chunk.support.TextSplitSupport;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 默认层级拆分实现。
 * <p>
 * 父块、子块均以句子为最小单位、按配置字符数"就近"收口（{@code parentSize} / {@code childSize} 仅作参考，
 * 实际在最近的句子边界结束，可略超过配置值），对文档翻译更友好；
 * 同一 Parent 下相邻 Child 之间按 {@code overlap} 以"完整句子"补充重叠，避免语义割裂。
 * Root 节点持有整篇文本（level = 0）。
 */
@Component
public class DefaultHierarchicalChunkStrategy implements HierarchicalChunkStrategy {

    private final TextSplitSupport textSplitSupport;
    private final ChunkFactorySupport chunkFactorySupport;

    public DefaultHierarchicalChunkStrategy(TextSplitSupport textSplitSupport,
                                            ChunkFactorySupport chunkFactorySupport) {
        this.textSplitSupport = textSplitSupport;
        this.chunkFactorySupport = chunkFactorySupport;
    }

    @Override
    public ChunkTree chunk(String content, ChunkConfig config) {
        DocumentChunk root = chunkFactorySupport.build(content, null, 0, 0, null);

        // 父块：按句子就近装箱至参考 parentSize
        List<String> parentTexts = textSplitSupport.packBySentence(content, config.getParentSize());

        List<DocumentChunk> parents = new ArrayList<>();
        List<DocumentChunk> children = new ArrayList<>();
        int childIndex = 0;

        for (int parentIndex = 0; parentIndex < parentTexts.size(); parentIndex++) {
            String parentText = parentTexts.get(parentIndex);
            DocumentChunk parent = chunkFactorySupport.build(parentText, root.getId(), 1, parentIndex, null);
            parents.add(parent);

            // 子块：在父块内按句子就近装箱至参考 childSize，再按完整句子补充 overlap
            List<String> childTexts = textSplitSupport.packBySentence(parentText, config.getChildSize());
            childTexts = textSplitSupport.applySentenceOverlap(childTexts, config.getOverlap());
            for (String childText : childTexts) {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("parentIndex", parentIndex);
                children.add(chunkFactorySupport.build(childText, parent.getId(), 2, childIndex++, metadata));
            }
        }

        return ChunkTree.builder()
                .root(root)
                .parents(parents)
                .children(children)
                .build();
    }
}
