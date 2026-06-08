package com.xx.aitranslation.util;

import com.xx.aitranslation.entity.TranslationSentence;
import org.springframework.util.ObjectUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class SummarySampleSelector {

    private static final int HEAD = 20;
    private static final int TAIL = 10;

    private SummarySampleSelector() {
    }

    public static List<TranslationSentence> select(List<TranslationSentence> segs, int maxSamples) {
        if (ObjectUtils.isEmpty(segs)) {
            return List.of();
        }
        List<TranslationSentence> sorted = segs.stream()
                .sorted(Comparator.comparing(TranslationSentence::getOrderNo))
                .toList();
        if (sorted.size() <= maxSamples) {
            return sorted;
        }

        int n = sorted.size();
        int headCount = Math.min(HEAD, n);
        int tailCount = Math.min(TAIL, n - headCount);
        int middleStart = headCount;
        int middleEnd = n - tailCount;
        int middleSize = middleEnd - middleStart;
        int middleSlots = maxSamples - headCount - tailCount;

        Set<Integer> seenOrderNos = new LinkedHashSet<>();
        List<TranslationSentence> result = new ArrayList<>(maxSamples);

        for (int i = 0; i < headCount; i++) {
            addIfAbsent(result, seenOrderNos, sorted.get(i));
        }

        if (middleSize > 0 && middleSlots > 0) {
            if (middleSlots >= middleSize) {
                for (int i = middleStart; i < middleEnd; i++) {
                    addIfAbsent(result, seenOrderNos, sorted.get(i));
                }
            } else if (middleSlots == 1) {
                addIfAbsent(result, seenOrderNos, sorted.get(middleStart + middleSize / 2));
            } else {
                for (int i = 0; i < middleSlots; i++) {
                    int idx = middleStart + (i * (middleSize - 1)) / (middleSlots - 1);
                    addIfAbsent(result, seenOrderNos, sorted.get(idx));
                }
            }
        }

        for (int i = n - tailCount; i < n; i++) {
            addIfAbsent(result, seenOrderNos, sorted.get(i));
        }

        return result;
    }

    private static void addIfAbsent(List<TranslationSentence> result, Set<Integer> seenOrderNos,
                                    TranslationSentence sentence) {
        if (seenOrderNos.add(sentence.getOrderNo())) {
            result.add(sentence);
        }
    }
}
