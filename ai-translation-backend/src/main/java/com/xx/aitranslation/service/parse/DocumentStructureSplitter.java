package com.xx.aitranslation.service.parse;

import org.springframework.util.ObjectUtils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 高级拆分核心：依据标题层级把有序的 {@link StructureBlock} 构建为章节树，
 * 再以「最小章节节点」为单位产出 {@link DocumentChunk}。
 *
 * <p>规则：
 * <ol>
 *     <li>标题块创建章节节点并按层级挂到最近的上级节点；正文块挂到当前章节节点。</li>
 *     <li>任何含正文的节点产出一个 Chunk，{@code parentTitle} 取层级父标题。</li>
 *     <li>Chunk 正文超过 {@code maxChunkSize} 时按段落切分为
 *     {@code 标题-Part1 / 标题-Part2 ...}，{@code parentTitle} 保留为该章节标题。</li>
 *     <li>不做句子级拆分。</li>
 * </ol>
 */
final class DocumentStructureSplitter {

    /** 默认 Chunk 字符上限（超过则按段落切分）。 */
    static final int DEFAULT_MAX_CHUNK_SIZE = 3000;

    private DocumentStructureSplitter() {
    }

    static List<DocumentChunk> split(List<StructureBlock> blocks, int maxChunkSize) {
        if (ObjectUtils.isEmpty(blocks)) {
            return List.of();
        }
        DocumentNode root = buildTree(blocks);
        List<DocumentChunk> chunks = new ArrayList<>();
        int[] seq = {0};
        collect(root, chunks, seq, maxChunkSize);
        return chunks;
    }

    /**
     * 依据标题层级构建章节树。正文挂到栈顶（当前）章节节点；标题前的正文挂到根节点。
     */
    private static DocumentNode buildTree(List<StructureBlock> blocks) {
        DocumentNode root = new DocumentNode(null, 0);
        Deque<DocumentNode> stack = new ArrayDeque<>();
        stack.push(root);
        for (StructureBlock block : blocks) {
            if (ObjectUtils.isEmpty(block.text())) {
                continue;
            }
            if (block.headingLevel() > 0) {
                DocumentNode node = new DocumentNode(block.text().trim(), block.headingLevel());
                while (stack.peek().level() >= node.level()) {
                    stack.pop();
                }
                stack.peek().addChild(node);
                stack.push(node);
            } else {
                stack.peek().addParagraph(block.text());
            }
        }
        return root;
    }

    /**
     * 前序遍历章节树，按文档顺序产出 Chunk。
     */
    private static void collect(DocumentNode node, List<DocumentChunk> chunks, int[] seq, int maxChunkSize) {
        if (node.hasContent()) {
            emit(node, chunks, seq, maxChunkSize);
        }
        for (DocumentNode child : node.children()) {
            collect(child, chunks, seq, maxChunkSize);
        }
    }

    private static void emit(DocumentNode node, List<DocumentChunk> chunks, int[] seq, int maxChunkSize) {
        String content = node.content();
        String parentTitle = parentTitle(node);
        if (content.length() <= maxChunkSize) {
            chunks.add(new DocumentChunk(nextId(seq), node.title(), parentTitle, node.level(), content));
            return;
        }
        List<String> parts = splitIntoParts(node.paragraphs(), maxChunkSize);
        int partNo = 1;
        for (String part : parts) {
            String partTitle = (ObjectUtils.isEmpty(node.title()) ? "" : node.title()) + "-Part" + partNo;
            chunks.add(new DocumentChunk(nextId(seq), partTitle, node.title(), node.level(), part));
            partNo++;
        }
    }

    /**
     * 层级父标题：取最近的有标题的祖先节点；根级正文返回 {@code null}。
     */
    private static String parentTitle(DocumentNode node) {
        DocumentNode parent = node.parent();
        while (parent != null && ObjectUtils.isEmpty(parent.title())) {
            parent = parent.parent();
        }
        return parent == null ? null : parent.title();
    }

    /**
     * 按段落贪心打包为不超过 maxChunkSize 的若干 Part；单段超长则按字符硬切。
     */
    private static List<String> splitIntoParts(List<String> paragraphs, int maxChunkSize) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String para : paragraphs) {
            if (ObjectUtils.isEmpty(para)) {
                continue;
            }
            if (para.length() > maxChunkSize) {
                flush(current, parts);
                for (int i = 0; i < para.length(); i += maxChunkSize) {
                    parts.add(para.substring(i, Math.min(para.length(), i + maxChunkSize)));
                }
                continue;
            }
            if (current.length() > 0 && current.length() + para.length() + 1 > maxChunkSize) {
                flush(current, parts);
            }
            if (current.length() > 0) {
                current.append('\n');
            }
            current.append(para);
        }
        flush(current, parts);
        return parts;
    }

    private static void flush(StringBuilder buffer, List<String> parts) {
        if (buffer.length() > 0) {
            parts.add(buffer.toString());
            buffer.setLength(0);
        }
    }

    private static String nextId(int[] seq) {
        return String.format("chunk-%04d", ++seq[0]);
    }
}
