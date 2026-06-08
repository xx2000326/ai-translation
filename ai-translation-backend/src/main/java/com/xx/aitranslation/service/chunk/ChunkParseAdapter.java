package com.xx.aitranslation.service.chunk;

import com.xx.aitranslation.service.chunk.config.ChunkStrategyType;
import com.xx.aitranslation.service.chunk.model.ChunkResult;
import com.xx.aitranslation.service.chunk.model.ChunkTree;
import com.xx.aitranslation.service.chunk.model.DocumentChunk;
import com.xx.aitranslation.service.parse.ParsedDocument;
import com.xx.aitranslation.service.parse.ParsedParagraph;
import com.xx.aitranslation.service.parse.ParsedSentence;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 拆分结果适配器：将通用拆分引擎产出的 {@link ChunkResult} 映射为翻译流程使用的 {@link ParsedDocument}。
 * <p>
 * 映射规则（每个翻译单元 = 1 段落 + 1 句子，整块送翻，保留上下文）：
 * <ul>
 *     <li>扁平策略（固定长度 / 段落 / 句子 / Markdown / 标题）：每个块即一个翻译单元。</li>
 *     <li>层级策略（父子）：取最细粒度的子块作为翻译单元；无子块的父块回退为单元。</li>
 * </ul>
 * 结构信息（{@code title / parentTitle / level}）仅对含层级的策略写入，供前端章节化展示。
 */
@Service
public class ChunkParseAdapter {

    /** 含层级 / 标题结构、需要在前端展示章节信息的策略。 */
    private static final List<ChunkStrategyType> STRUCTURAL = List.of(
            ChunkStrategyType.TITLE, ChunkStrategyType.MARKDOWN, ChunkStrategyType.HIERARCHICAL);

    /** 父块预览文本最大长度（用于无标题的层级父块作为分组标识展示）。 */
    private static final int PARENT_PREVIEW_MAX = 40;

    /** 判定相邻块为重叠（overlap）的最小重叠字符数，避免标点/短语造成的误判。 */
    private static final int MIN_OVERLAP_CHARS = 12;

    public ParsedDocument toParsedDocument(ChunkResult result) {
        List<DocumentChunk> units = selectUnits(result);
        boolean structural = !ObjectUtils.isEmpty(result.getStrategy()) && STRUCTURAL.contains(result.getStrategy());
        Map<String, String> titleById = buildTitleIndex(result);
        Map<String, String> parentPreviewById = buildParentPreviewIndex(result);

        List<ParsedParagraph> paragraphs = new ArrayList<>(units.size());
        int orderNo = 0;
        // 上一单元的原始内容（含其自身 overlap），用于检测并剔除当前块开头与之重叠的衔接内容。
        String prevRaw = null;
        for (DocumentChunk chunk : units) {
            String raw = ObjectUtils.isEmpty(chunk.getContent()) ? null : chunk.getContent();
            if (raw == null) {
                continue;
            }
            // 去除块间 overlap 前缀：overlap 仅用于翻译时的上文衔接，不落库，避免原文/译文重复。
            String content = stripOverlapPrefix(prevRaw, raw);
            prevRaw = raw;
            if (ObjectUtils.isEmpty(content)) {
                continue;
            }
            ParsedSentence sentence = new ParsedSentence(0, chunk.getId(), content);
            if (structural) {
                paragraphs.add(new ParsedParagraph(orderNo, chunk.getId(), "chunk", content,
                        List.of(sentence), titleById.get(chunk.getId()),
                        resolveParentTitle(chunk, titleById, parentPreviewById), chunk.getLevel()));
            } else {
                paragraphs.add(new ParsedParagraph(orderNo, chunk.getId(), "chunk", content, List.of(sentence)));
            }
            orderNo++;
        }
        return new ParsedDocument(paragraphs);
    }

    /**
     * 剔除当前块开头与上一块结尾重叠的衔接内容（取最长重叠并要求不小于 {@link #MIN_OVERLAP_CHARS}）。
     *
     * @param prev    上一单元原始内容，可为空
     * @param current 当前单元原始内容
     * @return 去除重叠前缀并去掉前导空白后的内容；无重叠时原样返回
     */
    private String stripOverlapPrefix(String prev, String current) {
        if (ObjectUtils.isEmpty(prev) || ObjectUtils.isEmpty(current)) {
            return current;
        }
        int max = Math.min(prev.length(), current.length());
        for (int len = max; len >= MIN_OVERLAP_CHARS; len--) {
            if (prev.regionMatches(prev.length() - len, current, 0, len)) {
                return current.substring(len).stripLeading();
            }
        }
        return current;
    }

    /**
     * 解析父块标题：优先用父块自身标题（标题层级 / Markdown 策略），
     * 父块无标题时（父子层级策略）回退为父块内容预览，便于前端按父级分组展示。
     */
    private String resolveParentTitle(DocumentChunk chunk, Map<String, String> titleById,
                                      Map<String, String> parentPreviewById) {
        String parentTitle = titleById.get(chunk.getParentId());
        if (!ObjectUtils.isEmpty(parentTitle)) {
            return parentTitle;
        }
        return parentPreviewById.get(chunk.getParentId());
    }

    /**
     * 选取翻译单元：层级策略取子块（无子块的父块回退），其余策略取全部块。
     */
    private List<DocumentChunk> selectUnits(ChunkResult result) {
        ChunkTree tree = result.getTree();
        if (ObjectUtils.isEmpty(tree)) {
            return ObjectUtils.isEmpty(result.getChunks()) ? List.of() : result.getChunks();
        }
        List<DocumentChunk> units = new ArrayList<>();
        for (DocumentChunk parent : tree.getParents()) {
            List<DocumentChunk> children = tree.getChildren().stream()
                    .filter(child -> parent.getId().equals(child.getParentId()))
                    .toList();
            if (children.isEmpty()) {
                units.add(parent);
            } else {
                units.addAll(children);
            }
        }
        if (units.isEmpty() && !ObjectUtils.isEmpty(tree.getRoot())) {
            units.add(tree.getRoot());
        }
        return units;
    }

    /**
     * 构建 块ID → 标题 索引，便于子块回填父块标题。
     */
    private Map<String, String> buildTitleIndex(ChunkResult result) {
        Map<String, String> index = new HashMap<>();
        if (ObjectUtils.isEmpty(result.getChunks())) {
            return index;
        }
        for (DocumentChunk chunk : result.getChunks()) {
            String title = extractTitle(chunk);
            if (!ObjectUtils.isEmpty(title)) {
                index.put(chunk.getId(), title);
            }
        }
        return index;
    }

    private String extractTitle(DocumentChunk chunk) {
        Map<String, Object> metadata = chunk.getMetadata();
        if (ObjectUtils.isEmpty(metadata)) {
            return null;
        }
        Object title = metadata.get("title");
        return title == null ? null : title.toString();
    }

    /**
     * 构建 父块ID → 内容预览 索引（仅层级策略有父块树）。
     * 父块无标题，取其内容前若干字符作为分组标识，供前端展示父子关系。
     */
    private Map<String, String> buildParentPreviewIndex(ChunkResult result) {
        Map<String, String> index = new HashMap<>();
        ChunkTree tree = result.getTree();
        if (ObjectUtils.isEmpty(tree) || ObjectUtils.isEmpty(tree.getParents())) {
            return index;
        }
        for (DocumentChunk parent : tree.getParents()) {
            String preview = preview(parent.getContent());
            if (!ObjectUtils.isEmpty(preview)) {
                index.put(parent.getId(), preview);
            }
        }
        return index;
    }

    /**
     * 内容预览：折叠空白并截断至 {@link #PARENT_PREVIEW_MAX} 字符。
     */
    private String preview(String content) {
        if (ObjectUtils.isEmpty(content)) {
            return null;
        }
        String oneLine = content.strip().replaceAll("\\s+", " ");
        if (oneLine.length() <= PARENT_PREVIEW_MAX) {
            return oneLine;
        }
        return oneLine.substring(0, PARENT_PREVIEW_MAX) + "…";
    }
}

