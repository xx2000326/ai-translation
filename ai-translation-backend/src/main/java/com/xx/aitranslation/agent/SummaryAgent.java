package com.xx.aitranslation.agent;

import com.xx.aitranslation.dto.SummaryResult;
import com.xx.aitranslation.entity.TranslationSentence;
import com.xx.aitranslation.enums.Language;
import com.xx.aitranslation.service.ai.ChatModelRouter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 汇总 Agent（V1 模块七）：对全文译文做风格统一（术语 / 语气 / 时态 / 人称 / 格式一致）。
 * <p>
 * 长文档按 {@link #BATCH_SIZE} 分批处理并以 orderNo 对齐；任一批解析失败时该批回退为原译文，
 * 保证不会因模型输出异常而丢失译文。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SummaryAgent {

    /** 每批送审的段落数 */
    private static final int BATCH_SIZE = 15;

    @Value("classpath:prompts/summary-prompt.st")
    private Resource summaryPrompt;

    private final ChatModelRouter chatModelRouter;

    /**
     * 全文风格统一。
     *
     * @param segs        待统一段落（使用其当前 translatedText）
     * @param requirement 任务整体翻译要求，可为空
     * @param sourceLang  源语言 code
     * @param targetLang  目标语言 code
     * @param modelCode   使用的模型 code
     * @return orderNo -> 统一后译文；缺失或失败的段落回退为原 translatedText
     */
    public Map<Integer, String> unify(List<TranslationSentence> segs, String requirement,
                                      String sourceLang, String targetLang, String modelCode) {
        Map<Integer, String> result = new HashMap<>();
        if (ObjectUtils.isEmpty(segs)) {
            return result;
        }
        for (int i = 0; i < segs.size(); i += BATCH_SIZE) {
            List<TranslationSentence> batch = segs.subList(i, Math.min(i + BATCH_SIZE, segs.size()));
            result.putAll(unifyBatch(batch, requirement, sourceLang, targetLang, modelCode));
        }
        return result;
    }

    private Map<Integer, String> unifyBatch(List<TranslationSentence> batch, String requirement,
                                            String sourceLang, String targetLang, String modelCode) {
        try {
            ChatClient client = chatModelRouter.client(modelCode);

            Map<String, Object> params = new HashMap<>();
            params.put("requirement", ObjectUtils.isEmpty(requirement) ? "（无特殊要求）" : requirement);
            params.put("sourceLang", Language.labelOf(sourceLang));
            params.put("targetLang", Language.labelOf(targetLang));
            params.put("pairs", buildPairs(batch));

            Prompt prompt = PromptTemplate.builder()
                    .resource(summaryPrompt)
                    .build()
                    .create(params);

            SummaryResult result = client.prompt(prompt).call().entity(SummaryResult.class);
            Map<Integer, String> merged = fallbackMap(batch);
            if (!ObjectUtils.isEmpty(result) && !ObjectUtils.isEmpty(result.segments())) {
                for (SummaryResult.SegmentText seg : result.segments()) {
                    if (!ObjectUtils.isEmpty(seg.text())) {
                        merged.put(seg.orderNo(), seg.text().trim());
                    }
                }
            }
            return merged;
        } catch (Exception e) {
            log.warn("全文风格统一批次失败(批量={}), 该批回退为原译文: {}", batch.size(), e.getMessage());
            return fallbackMap(batch);
        }
    }

    /**
     * 拼接各段 orderNo + 译文为统一输入文本。
     */
    private String buildPairs(List<TranslationSentence> segs) {
        StringBuilder sb = new StringBuilder();
        for (TranslationSentence seg : segs) {
            sb.append("orderNo: ").append(seg.getOrderNo()).append('\n')
                    .append("译文: ").append(ObjectUtils.isEmpty(seg.getTranslatedText()) ? "" : seg.getTranslatedText()).append('\n')
                    .append("---\n");
        }
        return sb.toString();
    }

    /**
     * 兜底映射：批次内所有段落回退为当前译文。
     */
    private Map<Integer, String> fallbackMap(List<TranslationSentence> segs) {
        Map<Integer, String> map = new HashMap<>();
        for (TranslationSentence seg : segs) {
            if (!ObjectUtils.isEmpty(seg.getOrderNo())) {
                map.put(seg.getOrderNo(), ObjectUtils.isEmpty(seg.getTranslatedText()) ? "" : seg.getTranslatedText());
            }
        }
        return map;
    }
}
