package com.xx.aitranslation.graph;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.xx.aitranslation.agent.AgentContext;
import com.xx.aitranslation.agent.ReviewAgent;
import com.xx.aitranslation.agent.SummaryAgent;
import com.xx.aitranslation.agent.TranslationAgent;
import com.xx.aitranslation.dto.ReviewResult;
import com.xx.aitranslation.entity.TranslationSentence;
import com.xx.aitranslation.entity.TranslationTask;
import com.xx.aitranslation.enums.TaskStatus;
import com.xx.aitranslation.enums.TaskStepCode;
import com.xx.aitranslation.service.DocumentParseService;
import com.xx.aitranslation.service.GlossaryService;
import com.xx.aitranslation.service.RagService;
import com.xx.aitranslation.service.TranslationTaskService;
import com.xx.aitranslation.service.chunk.ChunkEngine;
import com.xx.aitranslation.service.chunk.ChunkParseAdapter;
import com.xx.aitranslation.service.chunk.config.ChunkConfig;
import com.xx.aitranslation.service.chunk.config.ChunkStrategyType;
import com.xx.aitranslation.service.chunk.model.ChunkResult;
import com.xx.aitranslation.service.chunk.strategy.TitleChunkStrategy;
import com.xx.aitranslation.service.chunk.support.TextSplitSupport;
import com.xx.aitranslation.service.pipeline.SentenceTranslationQueue;
import com.xx.aitranslation.service.storage.FileStorageService;
import com.xx.aitranslation.support.TaskExtraDataSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Graph 节点容器：集中管理翻译管道的所有 NodeAction。
 * <p>
 * 每个方法对应一个节点，从 {@link OverAllState} 读取输入，返回更新到 state。
 * 共享 helper（translateOne、buildContextMap 等）也放在此类中。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GraphNodes {

    /** 审校及格分 */
    private static final int PASS_SCORE = 80;
    private static final int CONTEXT_CHARS = 200;

    private final TranslationTaskService translationTaskService;
    private final DocumentParseService documentParseService;
    private final FileStorageService fileStorageService;
    private final ChunkEngine chunkEngine;
    private final ChunkParseAdapter chunkParseAdapter;
    private final TranslationAgent translationAgent;
    private final ReviewAgent reviewAgent;
    private final SummaryAgent summaryAgent;
    private final GlossaryService glossaryService;
    private final RagService ragService;
    private final SentenceTranslationQueue sentenceTranslationQueue;
    private final TextSplitSupport textSplitSupport;
    private final TaskExtraDataSupport taskExtraDataSupport;

    // ======================== 节点实现 ========================

    /**
     * 节点：文档解析。复用 {@code TranslationPipeline.parseAsync()} 的解析逻辑。
     */
    public Map<String, Object> parseNode(OverAllState state) throws Exception {
        Long taskId = state.value("taskId", 0L);
        try {
            TranslationTask task = translationTaskService.getById(taskId);
            ChunkConfig config = buildChunkConfig(task);
            ChunkResult result;
            try (InputStream in = fileStorageService.download(task.getSourceFileKey())) {
                result = chunkEngine.chunk(in, task.getSourceFileName(), config);
            }
            documentParseService.saveParsedDocument(taskId, task, chunkParseAdapter.toParsedDocument(result));
            translationTaskService.completeParseStep(taskId);
            return Map.of("parseDone", true);
        } catch (Exception e) {
            log.error("文档解析失败, taskId={}", taskId, e);
            translationTaskService.fail(taskId, e.getMessage());
            return Map.of("parseFailed", true, "parseError", e.getMessage());
        }
    }

    /**
     * 节点：并发初翻译。
     */
    public Map<String, Object> translateNode(OverAllState state) throws Exception {
        Long taskId = state.value("taskId", 0L);
        translationTaskService.resetTranslations(taskId);

        List<TranslationSentence> sentences = translationTaskService.listSentences(taskId);
        Map<Long, String> contextById = buildContextMap(sentences);

        sentenceTranslationQueue.executeBatch(
                taskId, sentences,
                (sent, advice) -> translateOne(state, sent, advice, contextById),
                null, TaskStepCode.TRANSLATE);
        translationTaskService.completeTranslateStep(taskId);
        return Map.of("translateDone", true);
    }

    /**
     * 节点：AI 审校评分。
     */
    public Map<String, Object> reviewScoreNode(OverAllState state) throws Exception {
        Long taskId = state.value("taskId", 0L);
        List<TranslationSentence> segs = translationTaskService.listSentences(taskId);
        int round = state.value("reviewRound", 0) + 1;
        int segTotal = segs.size();
        translationTaskService.initReviewScoringProgress(taskId, round, segTotal);

        String requirement = state.value("requirement", "");
        String sourceLang = state.value("sourceLang", "");
        String targetLang = state.value("targetLang", "");
        String reviewModel = state.value("reviewModel", "");

        ReviewResult result = reviewAgent.review(
                segs, requirement, sourceLang, targetLang, reviewModel,
                scored -> translationTaskService.updateReviewProgress(taskId, scored, segTotal));
        translationTaskService.completeReviewScoringStep(taskId);

        int minScore = 100;
        int flagged = 0;
        if (result != null && result.segments() != null) {
            List<ReviewResult.SegmentReview> reviews = result.segments();
            Map<Integer, ReviewResult.SegmentReview> reviewByOrder = new HashMap<>();
            for (ReviewResult.SegmentReview r : reviews) {
                reviewByOrder.put(r.orderNo(), r);
            }

            for (TranslationSentence seg : segs) {
                ReviewResult.SegmentReview r = reviewByOrder.get(seg.getOrderNo());
                int score = r == null ? PASS_SCORE : r.score();
                String advice = r == null ? "" : r.advice();
                seg.setReviewScore(score);
                seg.setReviewAdvice(advice);
                seg.setReviewFlag(score < PASS_SCORE);
                translationTaskService.updateSentence(seg);
                minScore = Math.min(minScore, score);
                if (score < PASS_SCORE) flagged++;
            }
        }
        translationTaskService.saveReviewMeta(taskId, minScore, round);

        return Map.of("minScore", minScore, "flagCount", flagged, "reviewRound", round,
                "retranslateDone", false);
    }

    /**
     * 节点：带建议重翻低分句。
     */
    public Map<String, Object> retranslateNode(OverAllState state) throws Exception {
        Long taskId = state.value("taskId", 0L);
        List<TranslationSentence> allSentences = translationTaskService.listSentences(taskId);
        Map<Long, String> contextById = buildContextMap(allSentences);
        List<TranslationSentence> flagged = allSentences.stream()
                .filter(s -> Boolean.TRUE.equals(s.getReviewFlag()))
                .collect(Collectors.toList());

        translationTaskService.initReviewRetranslateProgress(taskId, flagged.size());
        sentenceTranslationQueue.executeBatch(
                taskId, flagged,
                (seg, ignored) -> translateOne(state, seg, seg.getReviewAdvice(), contextById),
                null, TaskStepCode.REVIEW_RETRANSLATE, false);
        translationTaskService.completeReviewRetranslateStep(taskId);

        return Map.of("retranslateDone", true);
    }

    /**
     * 节点：风格指南抽取。
     */
    public Map<String, Object> summaryGuideNode(OverAllState state) throws Exception {
        Long taskId = state.value("taskId", 0L);
        List<TranslationSentence> segs = translationTaskService.listSentences(taskId);

        boolean enableGlossary = state.value("enableGlossary", false);
        String tempGlossaryText = state.value("tempGlossary", "");
        String fullGlossary = enableGlossary
                ? mergeGlossary(glossaryService.buildGlossaryRules(state.value("customerId", 0L), concatOriginalTexts(segs)), tempGlossaryText)
                : tempGlossaryText;

        translationTaskService.startSummaryGuideStep(taskId);
        String styleGuide = summaryAgent.extractStyleGuide(
                segs, state.value("requirement", ""),
                state.value("sourceLang", ""), state.value("targetLang", ""),
                state.value("roleDesc", ""), state.value("styleDesc", ""),
                fullGlossary, state.value("translateModel", ""));
        translationTaskService.completeSummaryGuideStep(taskId);

        return Map.of("styleGuide", styleGuide);
    }

    /**
     * 节点：风格统一润色。
     */
    public Map<String, Object> summaryUnifyNode(OverAllState state) throws Exception {
        Long taskId = state.value("taskId", 0L);
        String styleGuide = state.value("styleGuide", "");
        List<TranslationSentence> segs = translationTaskService.listSentences(taskId);
        int total = segs.size();
        translationTaskService.initSummaryProgress(taskId, total);

        Map<Integer, String> unified = summaryAgent.unifyWithStyleGuide(
                segs, styleGuide,
                state.value("requirement", ""),
                state.value("sourceLang", ""), state.value("targetLang", ""),
                state.value("translateModel", ""),
                completed -> translationTaskService.updateSummaryProgress(taskId, completed, total));

        for (TranslationSentence seg : segs) {
            seg.setReviewedText(unified.getOrDefault(seg.getOrderNo(), seg.getTranslatedText()));
            translationTaskService.updateSentence(seg);
        }
        return Map.of("summaryDone", true);
    }

    /**
     * 节点：收尾落库 — 将最终译文写入 reviewedText，状态流转到 MANUAL_REVIEW。
     */
    public Map<String, Object> finalizeNode(OverAllState state) throws Exception {
        Long taskId = state.value("taskId", 0L);

        List<TranslationSentence> segs = translationTaskService.listSentences(taskId);
        for (TranslationSentence seg : segs) {
            if (ObjectUtils.isEmpty(seg.getReviewedText())) {
                seg.setReviewedText(seg.getTranslatedText());
                translationTaskService.updateSentence(seg);
            }
        }
        translationTaskService.transit(taskId, null, TaskStatus.MANUAL_REVIEW);
        return Map.of("done", true);
    }

    // ======================== 共享 Helper 方法 ========================

    /**
     * 翻译单个句子并写回（含术语 / RAG 注入、可选审校建议重翻）。
     *
     * @param contextById 上文参考映射（衔接上下文），由调用方构建后传入
     */
    private void translateOne(OverAllState state, TranslationSentence sent, String advice,
                              Map<Long, String> contextById) {
        Long customerId = state.value("customerId", 0L);
        String sourceLang = state.value("sourceLang", "");
        String targetLang = state.value("targetLang", "");
        String translateModel = state.value("translateModel", "");
        String roleDesc = state.value("roleDesc", "");
        String styleDesc = state.value("styleDesc", "");
        String ragRole = state.value("ragRole", "");
        String ragStyle = state.value("ragStyle", "");
        String tempGlossaryText = state.value("tempGlossary", "");
        boolean enableGlossary = state.value("enableGlossary", false);
        boolean enableHistory = state.value("enableHistory", false);

        String glossaryRules = enableGlossary
                ? mergeGlossary(glossaryService.buildGlossaryRules(customerId, sent.getOriginalText()), tempGlossaryText)
                : "";
        String ragContext = enableHistory
                ? ragService.buildRagContext(sent.getOriginalText(), customerId,
                ragRole, ragStyle, sourceLang, targetLang)
                : "";

        String contextPrefix = contextById.get(sent.getId());

        String translated = translationAgent.translate(AgentContext.builder()
                .text(sent.getOriginalText())
                .contextPrefix(contextPrefix)
                .sourceLang(sourceLang)
                .targetLang(targetLang)
                .role(roleDesc)
                .style(styleDesc)
                .glossaryRules(glossaryRules)
                .ragContext(ragContext)
                .reviewAdvice(advice)
                .modelCode(translateModel)
                .build());

        sent.setTranslatedText(translated);
        translationTaskService.updateSentence(sent);

        if (enableHistory) {
            ragService.saveMemory(sent.getOriginalText(), translated, customerId,
                    ragRole, ragStyle, sourceLang, targetLang);
        }
    }

    /**
     * 构建上文参考映射：每个句子的上文 = 文档顺序中前一句结尾的 CONTEXT_CHARS 字符。
     */
    private Map<Long, String> buildContextMap(List<TranslationSentence> sentences) {
        Map<Long, String> contextById = new HashMap<>();
        if (ObjectUtils.isEmpty(sentences)) {
            return contextById;
        }
        String prevText = null;
        for (TranslationSentence sent : sentences) {
            if (!ObjectUtils.isEmpty(prevText)) {
                String context = textSplitSupport.tailContext(prevText, CONTEXT_CHARS);
                if (!ObjectUtils.isEmpty(context)) {
                    contextById.put(sent.getId(), context);
                }
            }
            prevText = sent.getOriginalText();
        }
        return contextById;
    }

    private String mergeGlossary(String customerRules, String tempRules) {
        List<String> parts = new ArrayList<>();
        if (!ObjectUtils.isEmpty(customerRules)) parts.add(customerRules);
        if (!ObjectUtils.isEmpty(tempRules)) parts.add(tempRules);
        return String.join("\n", parts);
    }

    private String concatOriginalTexts(List<TranslationSentence> segs) {
        if (ObjectUtils.isEmpty(segs)) return "";
        return segs.stream()
                .map(TranslationSentence::getOriginalText)
                .filter(t -> !ObjectUtils.isEmpty(t))
                .collect(Collectors.joining("\n"));
    }

    private ChunkConfig buildChunkConfig(TranslationTask task) {
        ChunkStrategyType strategyType = ChunkStrategyType.parse(task.getChunkStrategy());
        ChunkConfig.ChunkConfigBuilder builder = ChunkConfig.builder()
                .strategy(strategyType)
                .headingSplitLevel(taskExtraDataSupport.resolveHeadingLevel(task.getExtraData()));
        if (!ObjectUtils.isEmpty(task.getChunkSize())) {
            builder.chunkSize(task.getChunkSize());
        } else if (strategyType == ChunkStrategyType.TITLE
                || strategyType == ChunkStrategyType.MARKDOWN
                || strategyType == null) {
            builder.chunkSize(TitleChunkStrategy.DEFAULT_SECTION_MAX_SIZE);
        }
        if (!ObjectUtils.isEmpty(task.getChunkOverlap())) builder.overlap(task.getChunkOverlap());
        if (!ObjectUtils.isEmpty(task.getChunkParentSize())) builder.parentSize(task.getChunkParentSize());
        if (!ObjectUtils.isEmpty(task.getChunkChildSize())) builder.childSize(task.getChunkChildSize());
        return builder.build();
    }
}
