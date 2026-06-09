package com.xx.aitranslation.service.parse;

import java.util.ArrayList;
import java.util.List;

/**
 * 文档结构树节点（高级拆分）。
 *
 * <p>由标题构成层级骨架，正文段落挂载到所属标题节点上。{@link #level} 为标题层级
 * （第X章=1、第X节=2、1=1、1.1=2、1.1.1=3 ...），根节点 level=0、title 为空。
 */
public class DocumentNode {

    /** 标题文本；根节点 / 前言节点为 {@code null}。 */
    private final String title;

    /** 标题层级，根节点为 0。 */
    private final int level;

    /** 挂载到本节点的正文段落（按原文顺序），便于超长时按段落切分。 */
    private final List<String> paragraphs = new ArrayList<>();

    private DocumentNode parent;

    private final List<DocumentNode> children = new ArrayList<>();

    public DocumentNode(String title, int level) {
        this.title = title;
        this.level = level;
    }

    public void addParagraph(String text) {
        paragraphs.add(text);
    }

    public void addChild(DocumentNode child) {
        child.parent = this;
        children.add(child);
    }

    /** 节点正文（段落以换行拼接）。 */
    public String content() {
        return String.join("\n", paragraphs);
    }

    public boolean hasContent() {
        return !paragraphs.isEmpty();
    }

    public String title() {
        return title;
    }

    public int level() {
        return level;
    }

    public List<String> paragraphs() {
        return paragraphs;
    }

    public DocumentNode parent() {
        return parent;
    }

    public List<DocumentNode> children() {
        return children;
    }
}
