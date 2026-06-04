package com.xx.aitranslation.service.pipeline;

import com.xx.aitranslation.dto.ReviewResult;
import com.xx.aitranslation.entity.TaskGlossary;
import com.xx.aitranslation.entity.TranslationSegment;
import com.xx.aitranslation.entity.TranslationTask;
import com.xx.aitranslation.enums.FileType;
import com.xx.aitranslation.enums.Language;
import com.xx.aitranslation.enums.TaskStatus;
import com.xx.aitranslation.service.GlossaryService;
import com.xx.aitranslation.service.RagService;
import com.xx.aitranslation.service.TranslationTaskService;
import com.xx.aitranslation.service.ai.ReviewLlmService;
import com.xx.aitranslation.service.ai.TranslationLlmService;
import com.xx.aitranslation.service.parse.DocumentParserFactory;
import com.xx.aitranslation.service.parse.ParsedBlock;
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
 * 翻译流程编排器：负责文档解析 / 翻译 / 审校等阶段的异步执行。
 * <p>
 * 注意：{@code @Async} 依赖 Spring 代理，必须由其他 Bean（如 Controller）调用本类方法触发，
 * 不可在本类内部自调用，否则异步不生效。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TranslationPipeline {

    /** 段落合格分数线，低于该分数需返工重译 */
    private static final int PASS_SCORE = 80;
    /** 审校最大轮次，超过后直接转入人工复核 */
    private static final int MAX_ROUND = 3;
    /** 文件翻译流程写入/检索 RAG 记忆所用的角色维度（任务级无 role/style，用固定值保证写读一致） */
    private static final String RAG_ROLE = "document";

    private final TranslationTaskService translationTaskService;
    private final FileStorageService fileStorageService;
    private final DocumentParserFactory parserFactory;
    private final TranslationLlmService translationLlmService;
    private final ReviewLlmService reviewLlmService;
    private final GlossaryService glossaryService;
    private final RagService ragService;

    /**
     * 异步解析源文件并落库段落，解析完成后将状态由 PARSING 流转为 PARSED。
     * <p>
     * 失败时记录错误原因并将任务置为 FAILED。
     *
     * @param taskId 任务ID
     */
    @Async("taskExecutor")
    public void parseAsync(Long taskId) {
        try {
            TranslationTask task = translationTaskService.getById(taskId);
            FileType fileType = FileType.valueOf(task.getSourceFileType());
            List<ParsedBlock> blocks;
            try (InputStream in = fileStorageService.download(task.getSourceFileKey())) {
                blocks = parserFactory.get(fileType).parse(in);
            }
            List<TranslationSegment> segments = new ArrayList<>();
            for (ParsedBlock block : blocks) {
                TranslationSegment segment = new TranslationSegment();
                segment.setTaskId(taskId);
                segment.setOrderNo(block.order());
                segment.setBlockType(block.type());
                segment.setOriginalText(block.text());
                segments.add(segment);
            }
            translationTaskService.saveSegments(segments);
            translationTaskService.transit(taskId, TaskStatus.PARSING, TaskStatus.PARSED);
        } catch (Exception e) {
            log.error("文档解析失败, taskId={}", taskId, e);
            translationTaskService.fail(taskId, e.getMessage());
        }
    }

    /**
     * 异步执行 AI 初翻译：逐段调用所选模型翻译，按需注入术语规则与 RAG 历史。
     * <p>
     * 翻译完成后：开启审校则进入逐段审校循环；否则转入人工复核状态。
     * 失败时记录错误原因并将任务置为 FAILED。
     *
     * @param taskId 任务ID
     */
    @Async("taskExecutor")
    public void translateAsync(Long taskId) {
        try {
            TranslationTask task = translationTaskService.getById(taskId);
            // 兼容重跑：不校验前置状态
            translationTaskService.transit(taskId, null, TaskStatus.TRANSLATING);

            String sourceLang = Language.labelOf(task.getSourceLang());
            String targetLang = Language.labelOf(task.getTargetLang());
            String model = task.getTranslateModel();
            // 任务级未存 role/style，初翻译传 null，由 LLM 服务内部填默认占位
            String role = null;
            String style = null;
            // RAG 记忆按客户 + 目标语种维度隔离（写入与检索必须一致，且不可为 null）
            String ragRole = RAG_ROLE;
            String ragStyle = ObjectUtils.isEmpty(task.getTargetLang()) ? "" : task.getTargetLang();

            boolean enableGlossary = !ObjectUtils.isEmpty(task.getEnableGlossary()) && task.getEnableGlossary();
            boolean enableHistory = !ObjectUtils.isEmpty(task.getEnableHistory()) && task.getEnableHistory();
            String tempGlossary = enableGlossary ? buildTempGlossary(taskId) : "";

            List<TranslationSegment> segments = translationTaskService.listSegments(taskId);
            for (TranslationSegment seg : segments) {
                String glossaryRules = enableGlossary
                        ? mergeGlossary(glossaryService.buildGlossaryRules(task.getCustomerId(), seg.getOriginalText()), tempGlossary)
                        : "";
                String ragContext = enableHistory
                        ? ragService.buildRagContext(seg.getOriginalText(), task.getCustomerId(), ragRole, ragStyle)
                        : "";
                String translated = translationLlmService.translate(seg.getOriginalText(), role, style,
                        sourceLang, targetLang, glossaryRules, ragContext, null, model);
                seg.setTranslatedText(translated);
                translationTaskService.updateSegment(seg);
                // 写入翻译记忆，供后续相同客户/语向的任务检索复用
                if (enableHistory) {
                    ragService.saveMemory(seg.getOriginalText(), task.getCustomerId(), ragRole, ragStyle);
                }
            }

            boolean enableReview = !ObjectUtils.isEmpty(task.getEnableReview()) && task.getEnableReview();
            if (enableReview) {
                reviewLoop(taskId);
            } else {
                translationTaskService.transit(taskId, null, TaskStatus.TRANSLATED);
                for (TranslationSegment seg : segments) {
                    seg.setReviewedText(seg.getTranslatedText());
                    translationTaskService.updateSegment(seg);
                }
                translationTaskService.transit(taskId, null, TaskStatus.MANUAL_REVIEW);
            }
        } catch (Exception e) {
            log.error("AI 翻译失败, taskId={}", taskId, e);
            translationTaskService.fail(taskId, e.getMessage());
        }
    }

    /**
     * 逐段审校循环（与 {@link #translateAsync} 同线程执行，无需 {@code @Async}）。
     * <p>
     * 每轮：审校模型逐段评分→写回段落分数/建议→对不合格段落带建议重译；
     * 当本轮全部达标或达到最大轮次时结束，复制译文为审校译文并转入人工复核。
     *
     * @param taskId 任务ID
     */
    public void reviewLoop(Long taskId) {
        TranslationTask task = translationTaskService.getById(taskId);
        String sourceLang = Language.labelOf(task.getSourceLang());
        String targetLang = Language.labelOf(task.getTargetLang());
        String requirement = task.getRequirement();
        String translateModel = task.getTranslateModel();
        String reviewModel = task.getReviewModel();
        boolean enableGlossary = !ObjectUtils.isEmpty(task.getEnableGlossary()) && task.getEnableGlossary();
        boolean enableHistory = !ObjectUtils.isEmpty(task.getEnableHistory()) && task.getEnableHistory();
        String tempGlossary = enableGlossary ? buildTempGlossary(taskId) : "";

        for (int round = 1; round <= MAX_ROUND; round++) {
            translationTaskService.transit(taskId, null, TaskStatus.REVIEWING);
            List<TranslationSegment> segs = translationTaskService.listSegments(taskId);

            ReviewResult result = reviewLlmService.review(segs, requirement, sourceLang, targetLang, reviewModel);
            Map<Integer, ReviewResult.SegmentReview> reviewByOrder = new HashMap<>();
            for (ReviewResult.SegmentReview r : result.segments()) {
                reviewByOrder.put(r.orderNo(), r);
            }

            int minScore = 100;
            List<TranslationSegment> flagged = new ArrayList<>();
            for (TranslationSegment seg : segs) {
                ReviewResult.SegmentReview r = reviewByOrder.get(seg.getOrderNo());
                int score = ObjectUtils.isEmpty(r) ? PASS_SCORE : r.score();
                String advice = ObjectUtils.isEmpty(r) ? "" : r.advice();
                seg.setReviewScore(score);
                seg.setReviewAdvice(advice);
                seg.setReviewFlag(score < PASS_SCORE);
                translationTaskService.updateSegment(seg);
                minScore = Math.min(minScore, score);
                if (score < PASS_SCORE) {
                    flagged.add(seg);
                }
            }
            translationTaskService.saveReviewMeta(taskId, minScore, round);

            if (ObjectUtils.isEmpty(flagged)) {
                break;
            }

            for (TranslationSegment seg : flagged) {
                String glossaryRules = enableGlossary
                        ? mergeGlossary(glossaryService.buildGlossaryRules(task.getCustomerId(), seg.getOriginalText()), tempGlossary)
                        : "";
                String ragContext = enableHistory
                        ? ragService.buildRagContext(seg.getOriginalText(), task.getCustomerId(), RAG_ROLE,
                            ObjectUtils.isEmpty(task.getTargetLang()) ? "" : task.getTargetLang())
                        : "";
                String retranslated = translationLlmService.translate(seg.getOriginalText(), null, null,
                        sourceLang, targetLang, glossaryRules, ragContext, seg.getReviewAdvice(), translateModel);
                seg.setTranslatedText(retranslated);
                translationTaskService.updateSegment(seg);
            }
        }

        List<TranslationSegment> finalSegs = translationTaskService.listSegments(taskId);
        for (TranslationSegment seg : finalSegs) {
            seg.setReviewedText(seg.getTranslatedText());
            translationTaskService.updateSegment(seg);
        }
        translationTaskService.transit(taskId, null, TaskStatus.REVIEW_DONE);
        translationTaskService.transit(taskId, null, TaskStatus.MANUAL_REVIEW);
    }

    /**
     * 将任务级临时术语转为多行 "term -> translation" 文本。
     */
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

    /**
     * 合并客户术语规则与任务临时术语规则，过滤空段后以换行拼接。
     */
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
}
