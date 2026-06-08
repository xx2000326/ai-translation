package com.xx.aitranslation.dto;

import com.xx.aitranslation.entity.TranslationSentence;
import java.util.List;

public record SummaryBatch(
        List<TranslationSentence> contextSegs,
        int primaryStartOrderNo,
        int primaryEndOrderNo
) {
    public int primarySentenceCount() {
        return Math.max(0, primaryEndOrderNo - primaryStartOrderNo + 1);
    }
}
