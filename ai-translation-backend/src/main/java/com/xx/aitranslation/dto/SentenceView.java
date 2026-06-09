package com.xx.aitranslation.dto;

import lombok.Data;

@Data
public class SentenceView {

    private Long id;

    private Long paragraphId;

    private Integer orderNo;

    private String blockType;

    /** 高级拆分：章节标题（或 标题-PartN）。 */
    private String title;

    /** 高级拆分：父标题（层级父章节标题）。 */
    private String parentTitle;

    /** 高级拆分：章节节点 ID（Part 块共享，聚合键）。 */
    private String sectionId;

    /** 高级拆分：原章节标题（不含 -PartN）。 */
    private String sectionTitle;

    /** 高级拆分：章节层级。 */
    private Integer level;

    private String originalText;

    private String translatedText;

    private String reviewedText;

    private String finalText;

    private Integer reviewScore;

    private String reviewAdvice;

    private Boolean reviewFlag;
}
