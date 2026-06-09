package com.xx.aitranslation.dto;

import java.util.List;

/**
 * 汇总 Agent 结构化结果：对各段译文做全文风格统一后的输出。
 * <p>
 * 用于 {@code ChatClient.prompt(...).call().entity(SummaryResult.class)} 的结构化输出绑定。
 *
 * @param segments 各段统一后的译文
 */
public record SummaryResult(List<SegmentText> segments) {

    /**
     * 单段统一结果。
     *
     * @param orderNo 句子顺序号（与 {@code TranslationSentence.orderNo} 对应）
     * @param text    统一润色后的译文
     */
    public record SegmentText(int orderNo, String text) {
    }
}
