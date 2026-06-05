package com.xx.aitranslation.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ParagraphDetailResponse {

    private Long id;

    private Integer orderNo;

    private String paraType;

    private String originalText;

    private List<SentenceItem> sentences = new ArrayList<>();

    @Data
    public static class SentenceItem {
        private Long id;
        private Integer orderNo;
        private Integer sentIndex;
        private String originalText;
        private String translatedText;
        private String reviewedText;
        private String finalText;
        private Integer reviewScore;
        private String reviewAdvice;
        private Boolean reviewFlag;
    }
}
