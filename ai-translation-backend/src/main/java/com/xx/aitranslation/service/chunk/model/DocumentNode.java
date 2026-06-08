package com.xx.aitranslation.service.chunk.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 标题结构树节点（参考 doc/V1/标题拆分.md）。
 * <p>
 * 通过标题层级关系构建文档树：节点持有自身标题、层级、归属正文，以及父/子节点引用。
 */
@Data
public class DocumentNode {

    /** 节点 ID（文档内唯一）。 */
    private String id;

    /** 标题文本（根节点为 {@code null}）。 */
    private String title;

    /** 标题层级：根=0，一级标题=1，二级标题=2 ……。 */
    private Integer level;

    /** 当前标题下、子标题之前的正文。 */
    private String content;

    /** 父节点（序列化时忽略，避免循环引用）。 */
    @JsonIgnore
    private DocumentNode parent;

    /** 子节点。 */
    private List<DocumentNode> children = new ArrayList<>();

    public void addChild(DocumentNode child) {
        child.setParent(this);
        this.children.add(child);
    }
}
