package com.xx.aitranslation.util;

import com.xx.aitranslation.dto.SummaryBatch;
import com.xx.aitranslation.entity.TranslationSentence;
import org.springframework.util.ObjectUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class SummaryBatchSplitter {

    private SummaryBatchSplitter() {
    }

    public static List<SummaryBatch> split(List<TranslationSentence> segs, int batchSize, int overlapSentences) {
        if (ObjectUtils.isEmpty(segs)) {
            return List.of();
        }
        List<TranslationSentence> sorted = segs.stream()
                .sorted(Comparator.comparing(TranslationSentence::getOrderNo))
                .toList();
        int n = sorted.size();
        int step = batchSize - overlapSentences;
        List<SummaryBatch> batches = new ArrayList<>();

        for (int ctxStart = 0; ctxStart < n; ctxStart += step) {
            int ctxEnd = Math.min(ctxStart + batchSize - 1, n - 1);
            List<TranslationSentence> context = sorted.subList(ctxStart, ctxEnd + 1);
            int primaryStart = sorted.get(ctxStart).getOrderNo();
            int primaryEnd = ctxEnd >= n - 1
                    ? sorted.get(ctxEnd).getOrderNo()
                    : sorted.get(ctxEnd - overlapSentences).getOrderNo();
            batches.add(new SummaryBatch(context, primaryStart, primaryEnd));
            if (ctxEnd >= n - 1) {
                break;
            }
        }
        return batches;
    }
}
