package com.xx.aitranslation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xx.aitranslation.common.BizException;
import com.xx.aitranslation.dto.TaskConfigRequest;
import com.xx.aitranslation.entity.Project;
import com.xx.aitranslation.entity.TaskGlossary;
import com.xx.aitranslation.entity.TranslationSegment;
import com.xx.aitranslation.entity.TranslationTask;
import com.xx.aitranslation.enums.TaskStatus;
import com.xx.aitranslation.mapper.ProjectMapper;
import com.xx.aitranslation.mapper.TaskGlossaryMapper;
import com.xx.aitranslation.mapper.TranslationSegmentMapper;
import com.xx.aitranslation.mapper.TranslationTaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.List;
import java.util.Optional;

/**
 * 翻译任务服务：任务读取、状态机流转与段落读写，供解析 / 翻译 / 审校 / 导出等后续阶段调用。
 */
@Service
@RequiredArgsConstructor
public class TranslationTaskService {

    private static final String DEFAULT_TRANSLATE_MODEL = "qwen-plus";
    private static final String DEFAULT_REVIEW_MODEL = "deepseek-chat";
    private static final String DEFAULT_SOURCE_LANG = "zh";
    private static final String DEFAULT_TARGET_LANG = "en";

    private final TranslationTaskMapper translationTaskMapper;
    private final TranslationSegmentMapper translationSegmentMapper;
    private final ProjectMapper projectMapper;
    private final TaskGlossaryMapper taskGlossaryMapper;
    private final RagService ragService;

    /**
     * 按 id 获取任务，不存在抛业务异常。
     */
    public TranslationTask getById(Long id) {
        TranslationTask task = translationTaskMapper.selectById(id);
        if (ObjectUtils.isEmpty(task)) {
            throw new BizException("task.not.found");
        }
        return task;
    }

    /**
     * 任务状态流转：当 {@code expected} 非空且当前状态与之不符时抛出非法状态异常，
     * 否则将状态置为 {@code next} 并持久化。
     *
     * @param id       任务ID
     * @param expected 期望的当前状态（为 null 时不校验）
     * @param next     目标状态
     * @return 更新后的任务
     */
    public TranslationTask transit(Long id, TaskStatus expected, TaskStatus next) {
        TranslationTask task = getById(id);
        if (!ObjectUtils.isEmpty(expected) && !expected.name().equals(task.getStatus())) {
            throw new BizException("task.status.illegal");
        }
        task.setStatus(next.name());
        translationTaskMapper.updateById(task);
        return task;
    }

    /**
     * 带"允许的当前状态集合"的状态流转：当 {@code allowed} 非空且当前状态不在其中时抛出非法状态异常，
     * 用于用户触发的入口（解析 / 翻译 / 完成 / 导出）做并发越级保护，避免在进行中状态被重复触发。
     *
     * @param id      任务ID
     * @param next    目标状态
     * @param allowed 允许的当前状态集合
     * @return 更新后的任务
     */
    public TranslationTask transitFromAny(Long id, TaskStatus next, TaskStatus... allowed) {
        TranslationTask task = getById(id);
        ensureStatusIn(task, allowed);
        task.setStatus(next.name());
        translationTaskMapper.updateById(task);
        return task;
    }

    /**
     * 校验任务当前状态是否在允许集合内，不在则抛 {@code task.status.illegal}。
     */
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

    /**
     * 查询任务下的所有段落，按段落顺序号升序。
     */
    public List<TranslationSegment> listSegments(Long taskId) {
        LambdaQueryWrapper<TranslationSegment> wrapper = new LambdaQueryWrapper<TranslationSegment>()
                .eq(TranslationSegment::getTaskId, taskId)
                .orderByAsc(TranslationSegment::getOrderNo);
        return translationSegmentMapper.selectList(wrapper);
    }

    /**
     * 批量保存段落（逐条插入）。
     */
    public void saveSegments(List<TranslationSegment> segments) {
        if (ObjectUtils.isEmpty(segments)) {
            return;
        }
        for (TranslationSegment segment : segments) {
            translationSegmentMapper.insert(segment);
        }
    }

    /**
     * 标记任务失败：置状态为 {@link TaskStatus#FAILED} 并记录失败原因。
     *
     * @param id  任务ID
     * @param msg 失败原因
     */
    public void fail(Long id, String msg) {
        TranslationTask task = getById(id);
        task.setStatus(TaskStatus.FAILED.name());
        task.setErrorMsg(msg);
        translationTaskMapper.updateById(task);
    }

    /**
     * 清空任务下的所有段落（重复解析前先清理旧段落）。
     *
     * @param taskId 任务ID
     */
    public void clearSegments(Long taskId) {
        LambdaQueryWrapper<TranslationSegment> wrapper = new LambdaQueryWrapper<TranslationSegment>()
                .eq(TranslationSegment::getTaskId, taskId);
        translationSegmentMapper.delete(wrapper);
    }

    /**
     * 更新单个段落。
     *
     * @param seg 待更新段落（需含 id）
     */
    public void updateSegment(TranslationSegment seg) {
        translationSegmentMapper.updateById(seg);
    }

    /**
     * 保存段落最终译文（人工审校确认）。
     *
     * @param segmentId 段落ID
     * @param finalText 最终译文
     */
    public void saveFinal(Long segmentId, String finalText) {
        TranslationSegment seg = translationSegmentMapper.selectById(segmentId);
        if (ObjectUtils.isEmpty(seg)) {
            throw new BizException("segment.not.found");
        }
        seg.setFinalText(finalText);
        translationSegmentMapper.updateById(seg);
    }

    /**
     * 完成任务：为每段回填最终译文（finalText 为空时取 reviewedText，再无则取 translatedText），
     * 将任务状态置为 {@link TaskStatus#COMPLETED}，并异步将翻译成果写入 RAG 向量库供后续参考。
     *
     * @param taskId 任务ID
     */
    public void complete(Long taskId) {
        TranslationTask task = getById(taskId);
        // 仅允许在人工审校阶段（或已完成/已导出，幂等重做）确认完成
        ensureStatusIn(task, TaskStatus.MANUAL_REVIEW, TaskStatus.COMPLETED, TaskStatus.EXPORTED);
        List<TranslationSegment> segments = listSegments(taskId);
        for (TranslationSegment seg : segments) {
            if (ObjectUtils.isEmpty(seg.getFinalText())) {
                String fallback = ObjectUtils.isEmpty(seg.getReviewedText())
                        ? seg.getTranslatedText()
                        : seg.getReviewedText();
                seg.setFinalText(fallback);
                updateSegment(seg);
            }
        }
        transit(taskId, null, TaskStatus.COMPLETED);

        // 异步将审校确认后的翻译对写入 RAG 向量库，失败不阻断主流程
        Project project = Optional.ofNullable(projectMapper.selectById(task.getProjectId()))
                .orElse(new Project());
        ragService.saveTranslationMemoriesAsync(segments, task.getCustomerId(),
                project.getRole(), project.getStyle());
    }

    /**
     * 解析段落最终文本：finalText 非空取 finalText，否则 reviewedText 非空取 reviewedText，否则取 translatedText。
     *
     * @param seg 段落
     * @return 最终文本
     */
    public String resolveFinalText(TranslationSegment seg) {
        if (!ObjectUtils.isEmpty(seg.getFinalText())) {
            return seg.getFinalText();
        }
        if (!ObjectUtils.isEmpty(seg.getReviewedText())) {
            return seg.getReviewedText();
        }
        return seg.getTranslatedText();
    }

    /**
     * 基于项目创建草稿任务：继承项目的客户与术语库开关，其余配置取默认值。
     *
     * @param projectId 项目ID
     * @return 新建并已落库的任务
     */
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
        task.setEnableHistory(false);
        task.setEnableReview(false);
        task.setStatus(TaskStatus.DRAFT.name());
        translationTaskMapper.insert(task);
        return task;
    }

    /**
     * 保存任务配置：字符串字段仅在非空时覆盖，布尔开关在非 null 时直接覆盖。
     *
     * @param taskId 任务ID
     * @param req    配置请求
     * @return 更新后的任务
     */
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
        translationTaskMapper.updateById(task);
        return task;
    }

    /**
     * 保存启动翻译时的配置：翻译模型、是否审校、审校模型。
     * <p>
     * 字符串字段仅在非空时覆盖，审校开关在非 null 时覆盖。
     *
     * @param id           任务ID
     * @param model        翻译模型 code
     * @param enableReview 是否启用 AI 审校
     * @param reviewModel  审校模型 code
     */
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

    /**
     * 保存审校元数据：综合评分与已执行轮次。
     *
     * @param id    任务ID
     * @param score 综合评分（一般取各段最小分）
     * @param round 已执行审校轮次
     */
    public void saveReviewMeta(Long id, int score, int round) {
        TranslationTask task = getById(id);
        task.setReviewScore(score);
        task.setReviewRound(round);
        translationTaskMapper.updateById(task);
    }

    /**
     * 记录已上传源文件信息，并将任务状态置为 {@link TaskStatus#FILE_UPLOADED}。
     *
     * @param taskId   任务ID
     * @param fileName 源文件原始名称
     * @param fileKey  源文件存储 key
     * @param fileType 源文件类型（{@link com.xx.aitranslation.enums.FileType} 的 name()）
     * @return 更新后的任务
     */
    public TranslationTask saveFile(Long taskId, String fileName, String fileKey, String fileType) {
        TranslationTask task = getById(taskId);
        task.setSourceFileName(fileName);
        task.setSourceFileKey(fileKey);
        task.setSourceFileType(fileType);
        task.setStatus(TaskStatus.FILE_UPLOADED.name());
        translationTaskMapper.updateById(task);
        return task;
    }

    /**
     * 新增任务级临时术语。
     */
    public TaskGlossary addGlossary(Long taskId, String term, String translation) {
        TaskGlossary glossary = new TaskGlossary();
        glossary.setTaskId(taskId);
        glossary.setTerm(term);
        glossary.setTranslation(translation);
        taskGlossaryMapper.insert(glossary);
        return glossary;
    }

    /**
     * 查询任务下的临时术语。
     */
    public List<TaskGlossary> listGlossary(Long taskId) {
        LambdaQueryWrapper<TaskGlossary> wrapper = new LambdaQueryWrapper<TaskGlossary>()
                .eq(TaskGlossary::getTaskId, taskId)
                .orderByAsc(TaskGlossary::getId);
        return taskGlossaryMapper.selectList(wrapper);
    }

    /**
     * 删除临时术语。
     */
    public void deleteGlossary(Long id) {
        taskGlossaryMapper.deleteById(id);
    }

    /**
     * 查询项目下的任务，按创建时间倒序。
     */
    public List<TranslationTask> listByProject(Long projectId) {
        LambdaQueryWrapper<TranslationTask> wrapper = new LambdaQueryWrapper<TranslationTask>()
                .eq(TranslationTask::getProjectId, projectId)
                .orderByDesc(TranslationTask::getCreateTime);
        return translationTaskMapper.selectList(wrapper);
    }
}
