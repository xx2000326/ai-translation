package com.xx.aitranslation.service.chunk.cleaner;

import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.Comparator;
import java.util.List;

/**
 * 清洗责任链：按 {@link DocumentCleaner#order()} 顺序串联执行全部清洗器。
 */
@Component
public class DocumentCleanerChain {

    private final List<DocumentCleaner> cleaners;

    public DocumentCleanerChain(List<DocumentCleaner> cleaners) {
        this.cleaners = cleaners.stream()
                .sorted(Comparator.comparingInt(DocumentCleaner::order))
                .toList();
    }

    /**
     * 依次执行所有清洗器。
     */
    public String clean(String content) {
        if (ObjectUtils.isEmpty(content)) {
            return "";
        }
        String result = content;
        for (DocumentCleaner cleaner : cleaners) {
            result = cleaner.clean(result);
        }
        return result;
    }
}
