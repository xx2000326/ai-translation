package com.xx.aitranslation.dto;

import java.util.List;

/**
 * AI 审校结构化结果：由审校模型对各段译文逐段评分并给出修改建议。
 * <p>
 * 用于 {@code ChatClient.prompt(...).call().entity(ReviewResult.class)} 的结构化输出绑定。
 *
 * @param segments 各段审校结果
 */
public record ReviewResult(List<SegmentReview> segments) {

    /**
     * 单段审校结果。
     *
     * @param orderNo 句子顺序号（与 {@code TranslationSentence.orderNo} 对应）
     * @param score   0-100 评分
     * @param advice  中文修改建议
     */
    public record SegmentReview(int orderNo, int score, String advice) {
    }
}
