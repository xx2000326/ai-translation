package com.xx.aitranslation.service.pipeline;

import com.xx.aitranslation.common.BizException;
import com.xx.aitranslation.dto.ReviewResult;
import com.xx.aitranslation.entity.TaskGlossary;
import com.xx.aitranslation.entity.TranslationSentence;
import com.xx.aitranslation.entity.TranslationTask;
import com.xx.aitranslation.enums.FileType;
import com.xx.aitranslation.enums.Language;
import com.xx.aitranslation.enums.TaskStatus;
import com.xx.aitranslation.service.DocumentParseService;
import com.xx.aitranslation.service.GlossaryService;
import com.xx.aitranslation.service.RagService;
import com.xx.aitranslation.service.TranslationTaskService;
import com.xx.aitranslation.service.ai.ReviewLlmService;
import com.xx.aitranslation.service.ai.TranslationLlmService;
import com.xx.aitranslation.service.parse.DocumentParserFactory;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class TranslationPipeline {

    private static final int PASS_SCORE = 80;
    private static final int MAX_ROUND = 3;
    private static final String RAG_ROLE = "document";

    private final TranslationTaskService translationTaskService;
    private final DocumentParseService documentParseService;
    private final FileStorageService fileStorageService;
    private final DocumentParserFactory parserFactory;
    private final TranslationLlmService translationLlmService;
    private final ReviewLlmService reviewLlmService;
    private final GlossaryService glossaryService;
    private final RagService ragService;

    @Async("taskExecutor")
    public void parseAsync(Long taskId) {
        try {
            TranslationTask task = translationTaskService.getById(taskId);
            FileType fileType = FileType.valueOf(task.getSourceFileType());
            ParsedDocument parsed;
            try (InputStream in = fileStorageService.download(task.getSourceFileKey())) {
                parsed = parserFactory.get(fileType).parse(in, task.getSourceLang());
            }
            documentParseService.saveParsedDocument(taskId, task, parsed);
            translationTaskService.transit(taskId, TaskStatus.PARSING, TaskStatus.PARSED);
        } catch (BizException e) {
            log.error("文档解析失败, taskId={}, code={}", taskId, e.getMessage());
            translationTaskService.fail(taskId, e.getMessage());
        } catch (Exception e) {
            log.error("文档解析失败, taskId={}", taskId, e);
            translationTaskService.fail(taskId, e.getMessage());
        }
    }

    @Async("taskExecutor")
    public void translateAsync(Long taskId) {
        try {
            TranslationTask task = translationTaskService.getById(taskId);
            translationTaskService.transit(taskId, null, TaskStatus.TRANSLATING);

            String sourceLang = Language.labelOf(task.getSourceLang());
            String targetLang = Language.labelOf(task.getTargetLang());
            String model = task.getTranslateModel();
            String role = null;
            String style = null;
            String ragRole = RAG_ROLE;
            String ragStyle = ObjectUtils.isEmpty(task.getTargetLang()) ? "" : task.getTargetLang();

            boolean enableGlossary = !ObjectUtils.isEmpty(task.getEnableGlossary()) && task.getEnableGlossary();
            boolean enableHistory = !ObjectUtils.isEmpty(task.getEnableHistory()) && task.getEnableHistory();
            String tempGlossary = enableGlossary ? buildTempGlossary(taskId) : "";

            List<TranslationSentence> sentences = translationTaskService.listSentences(taskId);
            for (TranslationSentence sent : sentences) {
                String glossaryRules = enableGlossary
                        ? mergeGlossary(glossaryService.buildGlossaryRules(task.getCustomerId(), sent.getOriginalText()), tempGlossary)
                        : "";
                String ragContext = enableHistory
                        ? ragService.buildRagContext(sent.getOriginalText(), task.getCustomerId(), ragRole, ragStyle)
                        : "";
                String translated = translationLlmService.translate(sent.getOriginalText(), role, style,
                        sourceLang, targetLang, glossaryRules, ragContext, null, model);
                sent.setTranslatedText(translated);
                translationTaskService.updateSentence(sent);
                if (enableHistory) {
                    ragService.saveMemory(sent.getOriginalText(), task.getCustomerId(), ragRole, ragStyle);
                }
            }

            boolean enableReview = !ObjectUtils.isEmpty(task.getEnableReview()) && task.getEnableReview();
            if (enableReview) {
                reviewLoop(taskId);
            } else {
                translationTaskService.transit(taskId, null, TaskStatus.TRANSLATED);
                for (TranslationSentence sent : sentences) {
                    sent.setReviewedText(sent.getTranslatedText());
                    translationTaskService.updateSentence(sent);
                }
                translationTaskService.transit(taskId, null, TaskStatus.MANUAL_REVIEW);
            }
        } catch (Exception e) {
            log.error("AI 翻译失败, taskId={}", taskId, e);
            translationTaskService.fail(taskId, e.getMessage());
        }
    }

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
            List<TranslationSentence> segs = translationTaskService.listSentences(taskId);

            ReviewResult result = reviewLlmService.review(segs, requirement, sourceLang, targetLang, reviewModel);
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
            translationTaskService.saveReviewMeta(taskId, minScore, round);

            if (ObjectUtils.isEmpty(flagged)) {
                break;
            }

            for (TranslationSentence seg : flagged) {
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
                translationTaskService.updateSentence(seg);
            }
        }

        List<TranslationSentence> finalSegs = translationTaskService.listSentences(taskId);
        for (TranslationSentence seg : finalSegs) {
            seg.setReviewedText(seg.getTranslatedText());
            translationTaskService.updateSentence(seg);
        }
        translationTaskService.transit(taskId, null, TaskStatus.REVIEW_DONE);
        translationTaskService.transit(taskId, null, TaskStatus.MANUAL_REVIEW);
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
}
