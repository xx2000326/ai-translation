package com.xx.aitranslation.service.parse;

import java.util.List;

/**
 * 解析产物（段落 / Chunk）。
 *
 * <p>普通拆分（按句 / 按段）下 {@code title / parentTitle / level / sectionId / sectionTitle} 为 {@code null}；
 * 高级拆分下携带章节层级与章节聚合信息。
 */
public record ParsedParagraph(
        int orderNo,
        String paraPosition,
        String paraType,
        String originalText,
        List<ParsedSentence> sentences,
        String title,
        String parentTitle,
        Integer level,
        String sectionId,
        String sectionTitle) {

    /** 普通拆分构造（无章节层级信息）。 */
    public ParsedParagraph(int orderNo, String paraPosition, String paraType,
                           String originalText, List<ParsedSentence> sentences) {
        this(orderNo, paraPosition, paraType, originalText, sentences, null, null, null, null, null);
    }

    /** 含层级、不含章节聚合键（旧 STRUCTURE 路径等）。 */
    public ParsedParagraph(int orderNo, String paraPosition, String paraType,
                           String originalText, List<ParsedSentence> sentences,
                           String title, String parentTitle, Integer level) {
        this(orderNo, paraPosition, paraType, originalText, sentences, title, parentTitle, level, null, null);
    }
}
