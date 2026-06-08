package com.xx.aitranslation.service.chunk.model;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 层级拆分结果：以 Root 为根，通过 {@link DocumentChunk#getParentId()} 维系父子关系。
 */
@Data
@Builder
public class ChunkTree {

    /** 根节点（level = 0，代表整篇文档）。 */
    private DocumentChunk root;

    /** Parent 块列表（level = 1）。 */
    @Builder.Default
    private List<DocumentChunk> parents = new ArrayList<>();

    /** Child 块列表（level = 2）。 */
    @Builder.Default
    private List<DocumentChunk> children = new ArrayList<>();

    /**
     * 将树结构压平为有序块列表：Root → 各 Parent →（其下 Child）。
     */
    public List<DocumentChunk> flatten() {
        List<DocumentChunk> all = new ArrayList<>();
        if (root != null) {
            all.add(root);
        }
        for (DocumentChunk parent : parents) {
            all.add(parent);
            children.stream()
                    .filter(child -> parent.getId().equals(child.getParentId()))
                    .forEach(all::add);
        }
        return all;
    }
}
