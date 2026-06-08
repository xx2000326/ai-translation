package com.xx.aitranslation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.xx.aitranslation.common.BizException;
import com.xx.aitranslation.dto.TaskConfigRequest;
import com.xx.aitranslation.entity.Project;
import com.xx.aitranslation.entity.TaskGlossary;
import com.xx.aitranslation.entity.TranslationSentence;
import com.xx.aitranslation.entity.TranslationTask;
import com.xx.aitranslation.enums.ParseGranularity;
import com.xx.aitranslation.enums.ProgressPhase;
import com.xx.aitranslation.enums.ReviewSubPhase;
import com.xx.aitranslation.enums.TaskStatus;
import com.xx.aitranslation.mapper.ProjectMapper;
import com.xx.aitranslation.mapper.TaskGlossaryMapper;
import com.xx.aitranslation.mapper.TranslationTaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TranslationTaskService {

    private static final String DEFAULT_TRANSLATE_MODEL = "qwen-plus";
    private static final String DEFAULT_REVIEW_MODEL = "deepseek-v4-flash";
    private static final String DEFAULT_SOURCE_LANG = "zh";
    private static final String DEFAULT_TARGET_LANG = "en";

    private final TranslationTaskMapper translationTaskMapper;
    private final ProjectMapper projectMapper;
    private final TaskGlossaryMapper taskGlossaryMapper;
    private final RagService ragService;
    private final DocumentParseService documentParseService;

    public TranslationTask getById(Long id) {
        TranslationTask task = translationTaskMapper.selectById(id);
        if (ObjectUtils.isEmpty(task)) {
            throw new BizException("task.not.found");
        }
        return task;
    }

    public TranslationTask transit(Long id, TaskStatus expected, TaskStatus next) {
        TranslationTask task = getById(id);
        if (!ObjectUtils.isEmpty(expected) && !expected.name().equals(task.getStatus())) {
            throw new BizException("task.status.illegal");
        }
        translationTaskMapper.update(null, new LambdaUpdateWrapper<TranslationTask>()
                .eq(TranslationTask::getId, id)
                .set(TranslationTask::getStatus, next.name()));
        task.setStatus(next.name());
        return task;
    }

    public TranslationTask transitFromAny(Long id, TaskStatus next, TaskStatus... allowed) {
        TranslationTask task = getById(id);
        ensureStatusIn(task, allowed);
        translationTaskMapper.update(null, new LambdaUpdateWrapper<TranslationTask>()
                .eq(TranslationTask::getId, id)
                .set(TranslationTask::getStatus, next.name()));
        task.setStatus(next.name());
        return task;
    }

    private void ensureStatusIn(TranslationTask task, TaskStatus... allowed) {
        if (ObjectUtils.isEmpty(allowed)) {
            return;
        }
        for (TaskStatus s : allowed) {
            if (s.name().equals(task.getStatus())) {
                return;
            }
        }
        throw new BizException("task.status.illegal");
    }

    public List<TranslationSentence> listSentences(Long taskId) {
        return documentParseService.listSentences(taskId);
    }

    public void fail(Long id, String msg) {
        translationTaskMapper.update(null, new LambdaUpdateWrapper<TranslationTask>()
                .eq(TranslationTask::getId, id)
                .set(TranslationTask::getStatus, TaskStatus.FAILED.name())
                .set(TranslationTask::getErrorMsg, msg));
    }

    public void clearParseResult(Long taskId) {
        documentParseService.clearParseResult(taskId);
    }

    public void updateSentence(TranslationSentence sentence) {
        documentParseService.updateSentence(sentence);
    }

    public void saveFinal(Long sentenceId, String finalText) {
        documentParseService.saveFinal(sentenceId, finalText);
    }

    public void complete(Long taskId) {
        TranslationTask task = getById(taskId);
        ensureStatusIn(task, TaskStatus.MANUAL_REVIEW, TaskStatus.COMPLETED, TaskStatus.EXPORTED);
        List<TranslationSentence> sentences = listSentences(taskId);
        for (TranslationSentence sent : sentences) {
            if (ObjectUtils.isEmpty(sent.getFinalText())) {
                String fallback = ObjectUtils.isEmpty(sent.getReviewedText())
                        ? sent.getTranslatedText()
                        : sent.getReviewedText();
                sent.setFinalText(fallback);
                updateSentence(sent);
            }
        }
        transit(taskId, null, TaskStatus.COMPLETED);

        Project project = Optional.ofNullable(projectMapper.selectById(task.getProjectId()))
                .orElse(new Project());
        ragService.saveTranslationMemoriesAsync(sentences, task.getCustomerId(),
                project.getRole(), project.getStyle(), task.getSourceLang(), task.getTargetLang());
    }

    public String resolveFinalText(TranslationSentence sentence) {
        return documentParseService.resolveFinalText(sentence);
    }

    public TranslationTask createFromProject(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (ObjectUtils.isEmpty(project)) {
            throw new BizException("project.not.found");
        }
        TranslationTask task = new TranslationTask();
        task.setProjectId(projectId);
        task.setCustomerId(project.getCustomerId());
        task.setEnableGlossary(project.getEnableGlossary());
        task.setTranslateModel(DEFAULT_TRANSLATE_MODEL);
        task.setReviewModel(DEFAULT_REVIEW_MODEL);
        task.setSourceLang(DEFAULT_SOURCE_LANG);
        task.setTargetLang(DEFAULT_TARGET_LANG);
        task.setParseGranularity(ParseGranularity.SENTENCE.name());
        task.setEnableHistory(false);
        task.setEnableReview(false);
        task.setEnableSummary(false);
        task.setStatus(TaskStatus.DRAFT.name());
        translationTaskMapper.insert(task);
        return task;
    }

    public TranslationTask saveConfig(Long taskId, TaskConfigRequest req) {
        TranslationTask task = getById(taskId);
        if (!ObjectUtils.isEmpty(req.getRequirement())) {
            task.setRequirement(req.getRequirement());
        }
        if (!ObjectUtils.isEmpty(req.getDescription())) {
            task.setDescription(req.getDescription());
        }
        if (!ObjectUtils.isEmpty(req.getSourceLang())) {
            task.setSourceLang(req.getSourceLang());
        }
        if (!ObjectUtils.isEmpty(req.getTargetLang())) {
            task.setTargetLang(req.getTargetLang());
        }
        if (!ObjectUtils.isEmpty(req.getParseGranularity())) {
            task.setParseGranularity(req.getParseGranularity());
        }
        if (!ObjectUtils.isEmpty(req.getTranslateModel())) {
            task.setTranslateModel(req.getTranslateModel());
        }
        if (!ObjectUtils.isEmpty(req.getReviewModel())) {
            task.setReviewModel(req.getReviewModel());
        }
        if (!ObjectUtils.isEmpty(req.getEnableGlossary())) {
            task.setEnableGlossary(req.getEnableGlossary());
        }
        if (!ObjectUtils.isEmpty(req.getEnableHistory())) {
            task.setEnableHistory(req.getEnableHistory());
        }
        if (!ObjectUtils.isEmpty(req.getEnableReview())) {
            task.setEnableReview(req.getEnableReview());
        }
        if (!ObjectUtils.isEmpty(req.getEnableSummary())) {
            task.setEnableSummary(req.getEnableSummary());
        }
        translationTaskMapper.updateById(task);
        return task;
    }

    public void saveTranslateConfig(Long id, String model, Boolean enableReview, String reviewModel) {
        TranslationTask task = getById(id);
        if (!ObjectUtils.isEmpty(model)) {
            task.setTranslateModel(model);
        }
        if (!ObjectUtils.isEmpty(enableReview)) {
            task.setEnableReview(enableReview);
        }
        if (!ObjectUtils.isEmpty(reviewModel)) {
            task.setReviewModel(reviewModel);
        }
        translationTaskMapper.updateById(task);
    }

    public void saveReviewMeta(Long id, int score, int round) {
        translationTaskMapper.update(null, new LambdaUpdateWrapper<TranslationTask>()
                .eq(TranslationTask::getId, id)
                .set(TranslationTask::getReviewScore, score)
                .set(TranslationTask::getReviewRound, round));
    }

    public void updateReviewRound(Long taskId, int round) {
        translationTaskMapper.update(null, new LambdaUpdateWrapper<TranslationTask>()
                .eq(TranslationTask::getId, taskId)
                .set(TranslationTask::getReviewRound, round));
    }

    public void initTranslateProgress(Long taskId, int total) {
        translationTaskMapper.update(null, new LambdaUpdateWrapper<TranslationTask>()
                .eq(TranslationTask::getId, taskId)
                .set(TranslationTask::getProgressPhase, ProgressPhase.TRANSLATE.name())
                .set(TranslationTask::getReviewSubPhase, null)
                .set(TranslationTask::getTotalSentences, total)
                .set(TranslationTask::getCompletedSentences, 0));
    }

    public void initReviewScoringProgress(Long taskId, int round, int total) {
        translationTaskMapper.update(null, new LambdaUpdateWrapper<TranslationTask>()
                .eq(TranslationTask::getId, taskId)
                .set(TranslationTask::getProgressPhase, ProgressPhase.REVIEW.name())
                .set(TranslationTask::getReviewSubPhase, ReviewSubPhase.SCORING.name())
                .set(TranslationTask::getReviewRound, round)
                .set(TranslationTask::getTotalSentences, total)
                .set(TranslationTask::getCompletedSentences, 0));
    }

    public void initReviewRetranslateProgress(Long taskId, int total) {
        translationTaskMapper.update(null, new LambdaUpdateWrapper<TranslationTask>()
                .eq(TranslationTask::getId, taskId)
                .set(TranslationTask::getProgressPhase, ProgressPhase.REVIEW.name())
                .set(TranslationTask::getReviewSubPhase, ReviewSubPhase.RETRANSLATE.name())
                .set(TranslationTask::getTotalSentences, total)
                .set(TranslationTask::getCompletedSentences, 0));
    }

    /** @deprecated 使用 {@link #initReviewScoringProgress} */
    public void initReviewProgress(Long taskId, int total) {
        initReviewScoringProgress(taskId, 1, total);
    }

    public void initSummaryProgress(Long taskId, int total) {
        translationTaskMapper.update(null, new LambdaUpdateWrapper<TranslationTask>()
                .eq(TranslationTask::getId, taskId)
                .set(TranslationTask::getProgressPhase, ProgressPhase.SUMMARY.name())
                .set(TranslationTask::getReviewSubPhase, null)
                .set(TranslationTask::getTotalSentences, total)
                .set(TranslationTask::getCompletedSentences, 0));
    }

    public void updateTranslateProgress(Long taskId, int completed, int total) {
        updatePhaseProgress(taskId, completed, total, ProgressPhase.TRANSLATE);
    }

    public void updateReviewProgress(Long taskId, int completed, int total) {
        translationTaskMapper.update(null, new LambdaUpdateWrapper<TranslationTask>()
                .eq(TranslationTask::getId, taskId)
                .set(TranslationTask::getProgressPhase, ProgressPhase.REVIEW.name())
                .set(TranslationTask::getCompletedSentences, completed)
                .set(TranslationTask::getTotalSentences, total));
    }

    public void updateSummaryProgress(Long taskId, int completed, int total) {
        updatePhaseProgress(taskId, completed, total, ProgressPhase.SUMMARY);
    }

    public void clearReviewSubPhase(Long taskId) {
        translationTaskMapper.update(null, new LambdaUpdateWrapper<TranslationTask>()
                .eq(TranslationTask::getId, taskId)
                .set(TranslationTask::getReviewSubPhase, null));
    }

    /** 风格统一完成后锁定 progress_phase 为 SUMMARY（列 NOT NULL） */
    public void markSummaryProgressDone(Long taskId) {
        TranslationTask task = getById(taskId);
        translationTaskMapper.update(null, new LambdaUpdateWrapper<TranslationTask>()
                .eq(TranslationTask::getId, taskId)
                .set(TranslationTask::getProgressPhase, ProgressPhase.SUMMARY.name())
                .set(TranslationTask::getReviewSubPhase, null)
                .set(TranslationTask::getCompletedSentences, task.getTotalSentences()));
    }

    private void updatePhaseProgress(Long taskId, int completed, int total, ProgressPhase phase) {
        translationTaskMapper.update(null, new LambdaUpdateWrapper<TranslationTask>()
                .eq(TranslationTask::getId, taskId)
                .set(TranslationTask::getProgressPhase, phase.name())
                .set(TranslationTask::getCompletedSentences, completed)
                .set(TranslationTask::getTotalSentences, total));
    }

    public TranslationTask saveFile(Long taskId, String fileName, String fileKey, String fileType) {
        TranslationTask task = getById(taskId);
        task.setSourceFileName(fileName);
        task.setSourceFileKey(fileKey);
        task.setSourceFileType(fileType);
        task.setStatus(TaskStatus.FILE_UPLOADED.name());
        translationTaskMapper.updateById(task);
        return task;
    }

    public TaskGlossary addGlossary(Long taskId, String term, String translation) {
        TaskGlossary glossary = new TaskGlossary();
        glossary.setTaskId(taskId);
        glossary.setTerm(term);
        glossary.setTranslation(translation);
        taskGlossaryMapper.insert(glossary);
        return glossary;
    }

    public List<TaskGlossary> listGlossary(Long taskId) {
        LambdaQueryWrapper<TaskGlossary> wrapper = new LambdaQueryWrapper<TaskGlossary>()
                .eq(TaskGlossary::getTaskId, taskId)
                .orderByAsc(TaskGlossary::getId);
        return taskGlossaryMapper.selectList(wrapper);
    }

    public void deleteGlossary(Long id) {
        taskGlossaryMapper.deleteById(id);
    }

    public List<TranslationTask> listByProject(Long projectId) {
        LambdaQueryWrapper<TranslationTask> wrapper = new LambdaQueryWrapper<TranslationTask>()
                .eq(TranslationTask::getProjectId, projectId)
                .orderByDesc(TranslationTask::getCreateTime);
        return translationTaskMapper.selectList(wrapper);
    }
}
