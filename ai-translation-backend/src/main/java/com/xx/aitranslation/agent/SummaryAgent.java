package com.xx.aitranslation.agent;

import com.xx.aitranslation.config.SummaryProperties;
import com.xx.aitranslation.dto.StyleGuideResult;
import com.xx.aitranslation.dto.SummaryBatch;
import com.xx.aitranslation.dto.SummaryResult;
import com.xx.aitranslation.entity.TranslationSentence;
import com.xx.aitranslation.enums.Language;
import com.xx.aitranslation.service.ai.ChatModelRouter;
import com.xx.aitranslation.service.pipeline.SummaryBatchQueue;
import com.xx.aitranslation.util.SummaryBatchSplitter;
import com.xx.aitranslation.util.SummarySampleSelector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;

/**
 * 汇总 Agent（V1 模块七）：StyleGuide 两阶段全文风格统一。
 * <p>
 * Phase 1 抽取风格指南；Phase 2 按固定句数重叠分批，经 {@link SummaryBatchQueue} 并行润色。
 * 任一批解析失败时该批主写入区间回退为原译文。
 */
@Slf4j
@Component
public class SummaryAgent {

    private static final String DEFAULT_ROLE = "通用译者";
    private static final String DEFAULT_STYLE = "正式";
    private static final String EMPTY_PLACEHOLDER = "（无）";
    private static final String EMPTY_REQUIREMENT = "（无特殊要求）";

    @Value("classpath:prompts/summary-prompt.st")
    private Resource summaryPrompt;

    @Value("classpath:prompts/summary-style-guide-prompt.st")
    private Resource styleGuidePrompt;

    private final ChatModelRouter chatModelRouter;
    private final SummaryProperties summaryProperties;
    private final SummaryBatchQueue summaryBatchQueue;

    public SummaryAgent(
            ChatModelRouter chatModelRouter,
            SummaryProperties summaryProperties,
            @Lazy SummaryBatchQueue summaryBatchQueue) {
        this.chatModelRouter = chatModelRouter;
        this.summaryProperties = summaryProperties;
        this.summaryBatchQueue = summaryBatchQueue;
    }

    /**
     * 全文风格统一：StyleGuide 抽取 + 并行分批润色。
     *
     * @param onProgress 累计已完成主写入句数回调（可为 null）
     * @return orderNo -> 统一后译文；缺失或失败的段落由 Pipeline 回退 translatedText
     */
    public Map<Integer, String> unify(
            List<TranslationSentence> segs,
            String requirement,
            String sourceLang,
            String targetLang,
            String roleDesc,
            String styleDesc,
            String glossaryRules,
            String modelCode,
            IntConsumer onProgress) {
        if (ObjectUtils.isEmpty(segs)) {
            return Map.of();
        }

        String styleGuide = extractStyleGuide(
                segs, requirement, sourceLang, targetLang, roleDesc, styleDesc, glossaryRules, modelCode);

        return unifyWithStyleGuide(
                segs, styleGuide, requirement, sourceLang, targetLang, modelCode, onProgress);
    }

    /**
     * Phase 2：在已有 StyleGuide 下并行分批润色。
     */
    public Map<Integer, String> unifyWithStyleGuide(
            List<TranslationSentence> segs,
            String styleGuide,
            String requirement,
            String sourceLang,
            String targetLang,
            String modelCode,
            IntConsumer onProgress) {
        if (ObjectUtils.isEmpty(segs)) {
            return Map.of();
        }

        List<SummaryBatch> batches = SummaryBatchSplitter.split(
                segs, summaryProperties.getBatchSize(), summaryProperties.getOverlapSentences());

        AtomicInteger done = new AtomicInteger(0);
        return summaryBatchQueue.executeUnify(
                batches,
                styleGuide,
                requirement,
                sourceLang,
                targetLang,
                modelCode,
                primaryCount -> {
                    if (!ObjectUtils.isEmpty(onProgress)) {
                        onProgress.accept(done.addAndGet(primaryCount));
                    }
                });
    }

    /**
     * Phase 1：从抽样译文提炼风格指南；失败时静态降级。
     */
    public String extractStyleGuide(
            List<TranslationSentence> segs,
            String requirement,
            String sourceLang,
            String targetLang,
            String roleDesc,
            String styleDesc,
            String glossaryRules,
            String modelCode) {
        List<TranslationSentence> samples = SummarySampleSelector.select(
                segs, summaryProperties.getStyleGuideMaxSamples());
        try {
            ChatClient client = chatModelRouter.client(modelCode);

            Map<String, Object> params = new HashMap<>();
            params.put("role", ObjectUtils.isEmpty(roleDesc) ? DEFAULT_ROLE : roleDesc);
            params.put("style", ObjectUtils.isEmpty(styleDesc) ? DEFAULT_STYLE : styleDesc);
            params.put("requirement", ObjectUtils.isEmpty(requirement) ? EMPTY_REQUIREMENT : requirement);
            params.put("sourceLang", Language.labelOf(sourceLang));
            params.put("targetLang", Language.labelOf(targetLang));
            params.put("glossary", ObjectUtils.isEmpty(glossaryRules) ? EMPTY_PLACEHOLDER : glossaryRules);
            params.put("samples", buildPairs(samples));

            Prompt prompt = PromptTemplate.builder()
                    .resource(styleGuidePrompt)
                    .build()
                    .create(params);

            StyleGuideResult result = client.prompt(prompt).call().entity(StyleGuideResult.class);
            if (ObjectUtils.isEmpty(result)) {
                throw new IllegalStateException("empty StyleGuideResult");
            }
            String text = result.toPromptText();
            if (ObjectUtils.isEmpty(text)) {
                throw new IllegalStateException("blank style guide text");
            }
            return text;
        } catch (Exception e) {
            log.warn("StyleGuide 抽取失败，使用静态降级: {}", e.getMessage());
            return buildStaticStyleGuide(requirement, roleDesc, styleDesc, glossaryRules);
        }
    }

    /**
     * 单批风格统一；仅主写入区间 orderNo 进入返回 Map。供 {@link SummaryBatchQueue} 调用。
     */
    public Map<Integer, String> unifyBatch(
            SummaryBatch batch,
            String styleGuide,
            String requirement,
            String sourceLang,
            String targetLang,
            String modelCode) {
        try {
            ChatClient client = chatModelRouter.client(modelCode);

            Map<String, Object> params = new HashMap<>();
            params.put("styleGuide", ObjectUtils.isEmpty(styleGuide) ? EMPTY_PLACEHOLDER : styleGuide);
            params.put("requirement", ObjectUtils.isEmpty(requirement) ? EMPTY_REQUIREMENT : requirement);
            params.put("sourceLang", Language.labelOf(sourceLang));
            params.put("targetLang", Language.labelOf(targetLang));
            params.put("pairs", buildPairs(batch.contextSegs()));

            Prompt prompt = PromptTemplate.builder()
                    .resource(summaryPrompt)
                    .build()
                    .create(params);

            SummaryResult result = client.prompt(prompt).call().entity(SummaryResult.class);
            Map<Integer, String> merged = fallbackPrimaryMap(batch);
            if (!ObjectUtils.isEmpty(result) && !ObjectUtils.isEmpty(result.segments())) {
                for (SummaryResult.SegmentText seg : result.segments()) {
                    if (!ObjectUtils.isEmpty(seg.text()) && isPrimaryOrderNo(batch, seg.orderNo())) {
                        merged.put(seg.orderNo(), seg.text().trim());
                    }
                }
            }
            return merged;
        } catch (Exception e) {
            log.warn("风格统一批次失败(primary={}-{}), 该批主区间回退为原译文: {}",
                    batch.primaryStartOrderNo(), batch.primaryEndOrderNo(), e.getMessage());
            return fallbackPrimaryMap(batch);
        }
    }

    private static boolean isPrimaryOrderNo(SummaryBatch batch, int orderNo) {
        return orderNo >= batch.primaryStartOrderNo() && orderNo <= batch.primaryEndOrderNo();
    }

    private String buildStaticStyleGuide(
            String requirement, String roleDesc, String styleDesc, String glossaryRules) {
        StringBuilder sb = new StringBuilder();
        appendStaticSection(sb, "翻译角色", roleDesc, DEFAULT_ROLE);
        appendStaticSection(sb, "翻译风格", styleDesc, DEFAULT_STYLE);
        appendStaticSection(sb, "整体要求", requirement, null);
        appendStaticSection(sb, "术语规则", glossaryRules, null);
        String text = sb.toString().trim();
        return ObjectUtils.isEmpty(text) ? EMPTY_PLACEHOLDER : text;
    }

    private static void appendStaticSection(StringBuilder sb, String title, String body, String defaultBody) {
        String value = ObjectUtils.isEmpty(body) ? defaultBody : body;
        if (ObjectUtils.isEmpty(value)) {
            return;
        }
        sb.append("## ").append(title).append('\n').append(value.trim()).append("\n\n");
    }

    private String buildPairs(List<TranslationSentence> segs) {
        StringBuilder sb = new StringBuilder();
        for (TranslationSentence seg : segs) {
            sb.append("orderNo: ").append(seg.getOrderNo()).append('\n')
                    .append("译文: ").append(ObjectUtils.isEmpty(seg.getTranslatedText()) ? "" : seg.getTranslatedText())
                    .append('\n')
                    .append("---\n");
        }
        return sb.toString();
    }

    private Map<Integer, String> fallbackPrimaryMap(SummaryBatch batch) {
        Map<Integer, String> map = new HashMap<>();
        for (TranslationSentence seg : batch.contextSegs()) {
            if (!ObjectUtils.isEmpty(seg.getOrderNo()) && isPrimaryOrderNo(batch, seg.getOrderNo())) {
                map.put(seg.getOrderNo(),
                        ObjectUtils.isEmpty(seg.getTranslatedText()) ? "" : seg.getTranslatedText());
            }
        }
        return map;
    }
}
