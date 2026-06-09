package com.xx.aitranslation.service.pipeline;

import com.xx.aitranslation.agent.AgentContext;
import com.xx.aitranslation.agent.ReviewAgent;
import com.xx.aitranslation.agent.SummaryAgent;
import com.xx.aitranslation.agent.TranslationAgent;
import com.xx.aitranslation.common.BizException;
import com.xx.aitranslation.dto.ReviewResult;
import com.xx.aitranslation.entity.Project;
import com.xx.aitranslation.entity.TaskGlossary;
import com.xx.aitranslation.entity.TranslationSentence;
import com.xx.aitranslation.entity.TranslationTask;
import com.xx.aitranslation.enums.TaskStatus;
import com.xx.aitranslation.enums.TaskStepCode;
import com.xx.aitranslation.enums.TranslationRole;
import com.xx.aitranslation.enums.TranslationStyle;
import com.xx.aitranslation.mapper.ProjectMapper;
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
import com.xx.aitranslation.service.parse.ParsedDocument;
import com.xx.aitranslation.service.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 翻译流水线编排器（V1 planner）：按状态机驱动 解析 → 并发翻译 → AI 审校循环 → 全文风格统一 → 人工审校。
 * <p>
 * 翻译以句子（{@code TranslationSentence}）为最小单元，通过 {@link SentenceTranslationQueue} 有界队列并发执行；
 * 角色 / 风格 / 整体要求从所属项目与任务注入各 Agent（V1 模块三 / 五 / 六 / 七）。
 * 解析阶段复用已验证的 {@code parse} 包，本编排器仅做衔接，不改解析逻辑。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TranslationPipeline {

    /** 审校及格分 */
    private static final int PASS_SCORE = 80;
    /** 审校最大循环轮次，防止死循环 */
    private static final int MAX_ROUND = 3;
    /** 构建“上文参考”时取前一翻译单元结尾的参考字符数。 */
    private static final int CONTEXT_CHARS = 200;

    private final TranslationTaskService translationTaskService;
    private final DocumentParseService documentParseService;
    private final FileStorageService fileStorageService;
    private final ChunkEngine chunkEngine;
    private final ChunkParseAdapter chunkParseAdapter;
    private final ProjectMapper projectMapper;
    private final TranslationAgent translationAgent;
    private final ReviewAgent reviewAgent;
    private final SummaryAgent summaryAgent;
    private final GlossaryService glossaryService;
    private final RagService ragService;
    private final SentenceTranslationQueue sentenceTranslationQueue;
    private final TextSplitSupport textSplitSupport;

    /**
     * 异步解析：下载源文件 → 文档拆分引擎拆分 → 映射为翻译单元落库 → 状态置 PARSED。
     */
    @Async("taskExecutor")
    public void parseAsync(Long taskId) {
        translationTaskService.startParseStep(taskId);
        try {
            TranslationTask task = translationTaskService.getById(taskId);
            ChunkConfig config = buildChunkConfig(task);
            ChunkResult result;
            try (InputStream in = fileStorageService.download(task.getSourceFileKey())) {
                result = chunkEngine.chunk(in, task.getSourceFileName(), config);
            }
            ParsedDocument parsed = chunkParseAdapter.toParsedDocument(result);
            documentParseService.saveParsedDocument(taskId, task, parsed);
            translationTaskService.completeParseStep(taskId);
            translationTaskService.transit(taskId, TaskStatus.PARSING, TaskStatus.PARSED);
        } catch (BizException e) {
            log.error("文档解析失败, taskId={}, code={}", taskId, e.getMessage());
            translationTaskService.fail(taskId, e.getMessage());
        } catch (Exception e) {
            log.error("文档解析失败, taskId={}", taskId, e);
            translationTaskService.fail(taskId, e.getMessage());
        }
    }

    /**
     * 由任务配置构建拆分引擎配置；为空字段交由全局默认值兜底。
     */
    private ChunkConfig buildChunkConfig(TranslationTask task) {
        ChunkStrategyType strategyType = ChunkStrategyType.parse(task.getChunkStrategy());
        ChunkConfig.ChunkConfigBuilder builder = ChunkConfig.builder()
                .strategy(strategyType);
        if (!ObjectUtils.isEmpty(task.getChunkSize())) {
            builder.chunkSize(task.getChunkSize());
        } else if (strategyType == ChunkStrategyType.TITLE || strategyType == null) {
            // AUTO 在 parse 后为 null；Word 等路径常命中 TITLE，默认章节块上限 200
            builder.chunkSize(TitleChunkStrategy.DEFAULT_SECTION_MAX_SIZE);
        }
        if (!ObjectUtils.isEmpty(task.getChunkOverlap())) {
            builder.overlap(task.getChunkOverlap());
        }
        if (!ObjectUtils.isEmpty(task.getChunkParentSize())) {
            builder.parentSize(task.getChunkParentSize());
        }
        if (!ObjectUtils.isEmpty(task.getChunkChildSize())) {
            builder.childSize(task.getChunkChildSize());
        }
        return builder.build();
    }

    /**
     * 异步翻译编排：并发初翻译 → （可选）AI 审校循环 → （可选）全文风格统一 → 人工审校。
     */
    @Async("taskExecutor")
    public void translateAsync(Long taskId) {
        try {
            TranslationTask task = translationTaskService.getById(taskId);
            TranslationContext ctx = buildContext(task);

            // 重新翻译场景：清空上一轮机翻 / 审校 / 定稿结果，确保全量重译、无脏数据（原文保留）。
            translationTaskService.resetTranslations(taskId);

            List<TranslationSentence> sentences = translationTaskService.listSentences(taskId);
            ctx.contextById = buildContextMap(sentences);
            translateSentencesConcurrently(ctx, sentences);
            translationTaskService.completeTranslateStep(taskId);

            if (ctx.enableReview) {
                reviewLoop(ctx);
            }

            finalizeTranslation(ctx);
            translationTaskService.transit(taskId, null, TaskStatus.MANUAL_REVIEW);
            if (ctx.enableSummary) {
                translationTaskService.markSummaryProgressDone(taskId);
            }
        } catch (Exception e) {
            log.error("AI 翻译失败, taskId={}", taskId, e);
            translationTaskService.fail(taskId, e.getMessage());
        }
    }

    /**
     * 构建“上文参考”映射：每个句子的上文 = 文档顺序中前一句结尾的若干完整句子。
     * <p>
     * 块间 overlap 已在解析落库时剔除，原文连续；此处在翻译时把前一单元的衔接上文作为语境传给 AI，
     * 既保留跨块连贯性，又不会产生重复译文。
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

    /**
     * 并发翻译所有句子：通过全局有界队列调度，Worker 数量由 {@code app.translation.concurrency} 控制。
     */
    private void translateSentencesConcurrently(TranslationContext ctx, List<TranslationSentence> sentences) {
        sentenceTranslationQueue.executeBatch(
                ctx.taskId, sentences, (sent, advice) -> translateOne(ctx, sent, advice), null,
                TaskStepCode.TRANSLATE);
    }

    /**
     * 翻译单个句子并写回（含术语 / RAG 注入、可选审校建议重翻、可选记忆写入）。
     *
     * @param advice 审校建议，初翻译传 null；反馈优化重翻时传上一轮建议
     */
    private void translateOne(TranslationContext ctx, TranslationSentence sent, String advice) {
        String glossaryRules = ctx.enableGlossary
                ? mergeGlossary(glossaryService.buildGlossaryRules(ctx.customerId, sent.getOriginalText()), ctx.tempGlossary)
                : "";
        String ragContext = ctx.enableHistory
                ? ragService.buildRagContext(sent.getOriginalText(), ctx.customerId,
                ctx.ragRole, ctx.ragStyle, ctx.sourceLang, ctx.targetLang)
                : "";

        String contextPrefix = ObjectUtils.isEmpty(ctx.contextById) ? null : ctx.contextById.get(sent.getId());

        String translated = translationAgent.translate(AgentContext.builder()
                .text(sent.getOriginalText())
                .contextPrefix(contextPrefix)
                .sourceLang(ctx.sourceLang)
                .targetLang(ctx.targetLang)
                .role(ctx.roleDesc)
                .style(ctx.styleDesc)
                .glossaryRules(glossaryRules)
                .ragContext(ragContext)
                .reviewAdvice(advice)
                .modelCode(ctx.translateModel)
                .build());

        sent.setTranslatedText(translated);
        translationTaskService.updateSentence(sent);

        if (ctx.enableHistory) {
            ragService.saveMemory(sent.getOriginalText(), translated, ctx.customerId,
                    ctx.ragRole, ctx.ragStyle, ctx.sourceLang, ctx.targetLang);
        }
    }

    /**
     * AI 审校循环（V1 模块五 / 六）：逐段评分，低于 {@value #PASS_SCORE} 分的段带建议重翻，最多 {@value #MAX_ROUND} 轮。
     */
    private void reviewLoop(TranslationContext ctx) {
        for (int round = 1; round <= MAX_ROUND; round++) {
            List<TranslationSentence> segs = translationTaskService.listSentences(ctx.taskId);
            int segTotal = segs.size();
            translationTaskService.initReviewScoringProgress(ctx.taskId, round, segTotal);

            ReviewResult result = reviewAgent.review(
                    segs, ctx.requirement, ctx.sourceLang, ctx.targetLang, ctx.reviewModel,
                    scored -> translationTaskService.updateReviewProgress(ctx.taskId, scored, segTotal));
            translationTaskService.completeReviewScoringStep(ctx.taskId);
            Map<Integer, ReviewResult.SegmentReview> reviewByOrder = new HashMap<>();
            for (ReviewResult.SegmentReview r : result.segments()) {
                reviewByOrder.put(r.orderNo(), r);
            }

            int minScore = 100;
            List<TranslationSentence> flagged = new ArrayList<>();
            for (TranslationSentence seg : segs) {
                ReviewResult.SegmentReview r = reviewByOrder.get(seg.getOrderNo());
                int score = ObjectUtils.isEmpty(r) ? PASS_SCORE : r.score();
                String advice = ObjectUtils.isEmpty(r) ? "" : r.advice();
                seg.setReviewScore(score);
                seg.setReviewAdvice(advice);
                seg.setReviewFlag(score < PASS_SCORE);
                translationTaskService.updateSentence(seg);
                minScore = Math.min(minScore, score);
                if (score < PASS_SCORE) {
                    flagged.add(seg);
                }
            }
            translationTaskService.saveReviewMeta(ctx.taskId, minScore, round);

            if (ObjectUtils.isEmpty(flagged)) {
                translationTaskService.completeReviewRetranslateStep(ctx.taskId);
                break;
            }

            // 带审校建议并发重翻被标记句（反馈优化 Agent）
            translationTaskService.initReviewRetranslateProgress(ctx.taskId, flagged.size());
            sentenceTranslationQueue.executeBatch(
                    ctx.taskId,
                    flagged,
                    (seg, ignored) -> translateOne(ctx, seg, seg.getReviewAdvice()),
                    null,
                    TaskStepCode.REVIEW_RETRANSLATE,
                    false);
            translationTaskService.completeReviewRetranslateStep(ctx.taskId);
        }
    }

    /**
     * 收尾：可选全文风格统一（V1 模块七），并把译文写入 reviewedText 作为人工审校初值。
     */
    private void finalizeTranslation(TranslationContext ctx) {
        List<TranslationSentence> segs = translationTaskService.listSentences(ctx.taskId);
        if (!ctx.enableSummary) {
            for (TranslationSentence seg : segs) {
                seg.setReviewedText(seg.getTranslatedText());
                translationTaskService.updateSentence(seg);
            }
            return;
        }

        String glossary = ctx.enableGlossary
                ? mergeGlossary(
                glossaryService.buildGlossaryRules(ctx.customerId, concatOriginalTexts(segs)),
                ctx.tempGlossary)
                : ctx.tempGlossary;
        int total = segs.size();
        translationTaskService.startSummaryGuideStep(ctx.taskId);
        String styleGuide = summaryAgent.extractStyleGuide(
                segs,
                ctx.requirement,
                ctx.sourceLang,
                ctx.targetLang,
                ctx.roleDesc,
                ctx.styleDesc,
                glossary,
                ctx.translateModel);
        translationTaskService.completeSummaryGuideStep(ctx.taskId);
        translationTaskService.initSummaryProgress(ctx.taskId, total);

        Map<Integer, String> unified = summaryAgent.unifyWithStyleGuide(
                segs,
                styleGuide,
                ctx.requirement,
                ctx.sourceLang,
                ctx.targetLang,
                ctx.translateModel,
                completed -> translationTaskService.updateSummaryProgress(ctx.taskId, completed, total));

        for (TranslationSentence seg : segs) {
            String reviewed = unified.getOrDefault(seg.getOrderNo(), seg.getTranslatedText());
            seg.setReviewedText(reviewed);
            translationTaskService.updateSentence(seg);
        }
    }

    private String concatOriginalTexts(List<TranslationSentence> segs) {
        if (ObjectUtils.isEmpty(segs)) {
            return "";
        }
        return segs.stream()
                .map(TranslationSentence::getOriginalText)
                .filter(t -> !ObjectUtils.isEmpty(t))
                .collect(Collectors.joining("\n"));
    }

    private TranslationContext buildContext(TranslationTask task) {
        Project project = task.getProjectId() == null ? null : projectMapper.selectById(task.getProjectId());

        TranslationContext ctx = new TranslationContext();
        ctx.taskId = task.getId();
        ctx.customerId = task.getCustomerId();
        ctx.sourceLang = task.getSourceLang();
        ctx.targetLang = task.getTargetLang();
        ctx.requirement = task.getRequirement();
        ctx.translateModel = task.getTranslateModel();
        ctx.reviewModel = task.getReviewModel();
        ctx.enableGlossary = isTrue(task.getEnableGlossary());
        ctx.enableHistory = isTrue(task.getEnableHistory());
        ctx.enableReview = isTrue(task.getEnableReview());
        ctx.enableSummary = isTrue(task.getEnableSummary());

        String roleCode = project == null ? null : project.getRole();
        String styleCode = project == null ? null : project.getStyle();
        TranslationRole role = TranslationRole.fromCode(roleCode);
        TranslationStyle style = TranslationStyle.fromCode(styleCode);
        ctx.roleDesc = role == null ? null : role.getDescription();
        ctx.styleDesc = style == null ? null : style.getDescription();
        // RAG 记忆维度用 role/style 的 code，与人工完成后写入记忆保持一致
        ctx.ragRole = ObjectUtils.isEmpty(roleCode) ? "" : roleCode;
        ctx.ragStyle = ObjectUtils.isEmpty(styleCode) ? "" : styleCode;
        ctx.tempGlossary = ctx.enableGlossary ? buildTempGlossary(task.getId()) : "";
        return ctx;
    }

    private boolean isTrue(Boolean value) {
        return !ObjectUtils.isEmpty(value) && value;
    }

    private String buildTempGlossary(Long taskId) {
        List<TaskGlossary> list = translationTaskService.listGlossary(taskId);
        if (ObjectUtils.isEmpty(list)) {
            return "";
        }
        return list.stream()
                .filter(g -> !ObjectUtils.isEmpty(g.getTerm()))
                .map(g -> g.getTerm() + " -> " + g.getTranslation())
                .collect(Collectors.joining("\n"));
    }

    private String mergeGlossary(String customerRules, String tempRules) {
        List<String> parts = new ArrayList<>();
        if (!ObjectUtils.isEmpty(customerRules)) {
            parts.add(customerRules);
        }
        if (!ObjectUtils.isEmpty(tempRules)) {
            parts.add(tempRules);
        }
        return String.join("\n", parts);
    }

    /**
     * 一次翻译运行的上下文快照，避免在并发翻译中反复读取任务 / 项目配置。
     */
    private static class TranslationContext {
        private Long taskId;
        private Long customerId;
        private String sourceLang;
        private String targetLang;
        private String requirement;
        private String translateModel;
        private String reviewModel;
        private String roleDesc;
        private String styleDesc;
        private String ragRole;
        private String ragStyle;
        private String tempGlossary;
        /** 句子ID → 上文参考（前一翻译单元的衔接上文，仅作语境，不翻译）。 */
        private Map<Long, String> contextById;
        private boolean enableGlossary;
        private boolean enableHistory;
        private boolean enableReview;
        private boolean enableSummary;
    }
}
