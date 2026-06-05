package com.xx.aitranslation.dto;

import lombok.Data;

@Data
public class SentenceView {

    private Long id;

    private Long paragraphId;

    private Integer orderNo;

    private String blockType;

    private String originalText;

    private String translatedText;

    private String reviewedText;

    private String finalText;

    private Integer reviewScore;

    private String reviewAdvice;

    private Boolean reviewFlag;
}
