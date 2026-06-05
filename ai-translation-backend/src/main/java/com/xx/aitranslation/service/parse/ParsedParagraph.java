package com.xx.aitranslation.service.parse;

import java.util.List;

public record ParsedParagraph(
        int orderNo,
        String paraPosition,
        String paraType,
        String originalText,
        List<ParsedSentence> sentences) {
}
