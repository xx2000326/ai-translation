package com.xx.aitranslation.service.chunk.reader;

import org.springframework.ai.document.Document;
import org.springframework.util.ObjectUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 基于 Spring AI {@link org.springframework.ai.document.DocumentReader} 的读取器抽象基类，
 * 负责将抽取出的多个 {@link Document} 合并为统一纯文本。
 */
public abstract class AbstractSpringAiDocumentReader implements DocumentReader {

    /** 多个 Document 间的拼接分隔符（空行，便于段落策略识别）。 */
    private static final String DOCUMENT_SEPARATOR = "\n\n";

    /**
     * 将 Spring AI 抽取的 Document 列表合并为纯文本。
     */
    protected String joinText(List<Document> documents) {
        if (ObjectUtils.isEmpty(documents)) {
            return "";
        }
        return documents.stream()
                .map(Document::getText)
                .filter(text -> !ObjectUtils.isEmpty(text))
                .collect(Collectors.joining(DOCUMENT_SEPARATOR));
    }
}
