package com.xx.aitranslation.service.ai;

import com.xx.aitranslation.dto.ReviewResult;
import com.xx.aitranslation.entity.TranslationSentence;
import com.xx.aitranslation.enums.Language;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 审校 LLM 服务：将原文 / 译文成对喂给审校模型，按段给出评分与中文修改建议（结构化输出）。
 * <p>
 * 长文档会按 {@link #BATCH_SIZE} 分批送审，避免单次 Prompt 过长导致输出被截断、
 * JSON 解析失败而整篇被静默判为通过；单批失败仅该批回退，不影响其余批次。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewLlmService {

    /** 解析失败兜底分（视为通过，避免审校循环卡死） */
    private static final int FALLBACK_SCORE = 80;
    private static final String EMPTY_ADVICE = "无";
    /** 每批送审的段落数，避免长文档超出模型上下文/输出上限 */
    private static final int BATCH_SIZE = 15;

    @Value("classpath:prompts/review-prompt.st")
    private Resource reviewPrompt;

    private final ChatModelRouter chatModelRouter;

    /**
     * 逐段审校（按 {@link #BATCH_SIZE} 分批执行后合并）。
     *
     * @param segs        待审校段落（含原文与当前译文）
     * @param requirement 任务整体翻译要求，可为空
     * @param sourceLang  源语言 code
     * @param targetLang  目标语言 code
     * @param reviewModel 审校模型 code
     * @return 结构化审校结果；某批解析异常时该批回退为 {@value #FALLBACK_SCORE} 分
     */
    public ReviewResult review(List<TranslationSentence> segs, String requirement,
                               String sourceLang, String targetLang, String reviewModel) {
        if (ObjectUtils.isEmpty(segs)) {
            return new ReviewResult(List.of());
        }
        List<ReviewResult.SegmentReview> merged = new ArrayList<>();
        for (int i = 0; i < segs.size(); i += BATCH_SIZE) {
            List<TranslationSentence> batch = segs.subList(i, Math.min(i + BATCH_SIZE, segs.size()));
            merged.addAll(reviewBatch(batch, requirement, sourceLang, targetLang, reviewModel));
        }
        return new ReviewResult(merged);
    }

    /**
     * 审校单个批次，失败时仅该批回退为通过。
     */
    private List<ReviewResult.SegmentReview> reviewBatch(List<TranslationSentence> batch, String requirement,
                                                         String sourceLang, String targetLang, String reviewModel) {
        try {
            ChatClient client = chatModelRouter.client(reviewModel);

            Map<String, Object> params = new HashMap<>();
            params.put("requirement", ObjectUtils.isEmpty(requirement) ? "（无特殊要求）" : requirement);
            params.put("sourceLang", Language.labelOf(sourceLang));
            params.put("targetLang", Language.labelOf(targetLang));
            params.put("pairs", buildPairs(batch));

            Prompt prompt = PromptTemplate.builder()
                    .resource(reviewPrompt)
                    .build()
                    .create(params);

            ReviewResult result = client.prompt(prompt).call().entity(ReviewResult.class);
            if (ObjectUtils.isEmpty(result) || ObjectUtils.isEmpty(result.segments())) {
                return fallbackList(batch);
            }
            return result.segments();
        } catch (Exception e) {
            log.warn("AI 审校批次解析失败(批量={}), 该批回退为默认通过: {}", batch.size(), e.getMessage());
            return fallbackList(batch);
        }
    }

    /**
     * 拼接各段 orderNo + 原文 + 译文为审校输入文本。
     */
    private String buildPairs(List<TranslationSentence> segs) {
        StringBuilder sb = new StringBuilder();
        for (TranslationSentence seg : segs) {
            sb.append("orderNo: ").append(seg.getOrderNo()).append('\n')
                    .append("原文: ").append(ObjectUtils.isEmpty(seg.getOriginalText()) ? "" : seg.getOriginalText()).append('\n')
                    .append("译文: ").append(ObjectUtils.isEmpty(seg.getTranslatedText()) ? "" : seg.getTranslatedText()).append('\n')
                    .append("---\n");
        }
        return sb.toString();
    }

    /**
     * 兜底列表：批次内所有段落给 {@value #FALLBACK_SCORE} 分，视为通过。
     */
    private List<ReviewResult.SegmentReview> fallbackList(List<TranslationSentence> segs) {
        return segs.stream()
                .map(s -> new ReviewResult.SegmentReview(
                        s.getOrderNo() == null ? 0 : s.getOrderNo(), FALLBACK_SCORE, EMPTY_ADVICE))
                .toList();
    }
}
