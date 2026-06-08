package com.xx.aitranslation.service.chunk.cleaner;

/**
 * 文档清洗器：对原始文本做归一化处理。
 * <p>
 * 多个清洗器通过 {@link #order()} 排序后串联执行（责任链）。
 */
public interface DocumentCleaner {

    /**
     * 清洗文本。
     *
     * @param content 输入文本
     * @return 清洗后的文本
     */
    String clean(String content);

    /**
     * 执行顺序，值越小越先执行。
     */
    default int order() {
        return 0;
    }
}
