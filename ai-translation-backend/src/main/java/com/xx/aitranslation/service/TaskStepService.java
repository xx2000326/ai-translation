package com.xx.aitranslation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.xx.aitranslation.dto.TaskStepDto;
import com.xx.aitranslation.entity.TranslationTaskStep;
import com.xx.aitranslation.enums.TaskStepCode;
import com.xx.aitranslation.enums.TaskStepStatus;
import com.xx.aitranslation.mapper.TranslationTaskStepMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

/**
 * 翻译任务步骤进度：读写 {@code translation_task_step} 子表。
 */
@Service
@RequiredArgsConstructor
public class TaskStepService {

    private static final EnumSet<TaskStepCode> AGENT_STEPS = EnumSet.of(
            TaskStepCode.TRANSLATE,
            TaskStepCode.REVIEW_SCORE,
            TaskStepCode.REVIEW_RETRANSLATE,
            TaskStepCode.SUMMARY_GUIDE,
            TaskStepCode.SUMMARY_UNIFY);

    private final TranslationTaskStepMapper stepMapper;

    public List<TaskStepDto> listByTask(Long taskId) {
        LambdaQueryWrapper<TranslationTaskStep> wrapper = new LambdaQueryWrapper<TranslationTaskStep>()
                .eq(TranslationTaskStep::getTaskId, taskId)
                .orderByAsc(TranslationTaskStep::getOrderNo);
        return stepMapper.selectList(wrapper).stream().map(TaskStepDto::from).toList();
    }

    public Optional<TranslationTaskStep> getStep(Long taskId, TaskStepCode code) {
        return Optional.ofNullable(stepMapper.selectOne(new LambdaQueryWrapper<TranslationTaskStep>()
                .eq(TranslationTaskStep::getTaskId, taskId)
                .eq(TranslationTaskStep::getStepCode, code.name())));
    }

    public Optional<TaskStepDto> getCurrentStep(Long taskId) {
        List<TranslationTaskStep> steps = stepMapper.selectList(new LambdaQueryWrapper<TranslationTaskStep>()
                .eq(TranslationTaskStep::getTaskId, taskId)
                .orderByAsc(TranslationTaskStep::getOrderNo));
        for (TranslationTaskStep step : steps) {
            if (TaskStepStatus.RUNNING.name().equals(step.getStatus())) {
                return Optional.of(TaskStepDto.from(step));
            }
        }
        TranslationTaskStep lastDone = null;
        for (TranslationTaskStep step : steps) {
            if (TaskStepStatus.DONE.name().equals(step.getStatus())) {
                lastDone = step;
            }
        }
        return ObjectUtils.isEmpty(lastDone) ? Optional.empty() : Optional.of(TaskStepDto.from(lastDone));
    }

    public void deleteByTaskId(Long taskId) {
        stepMapper.delete(new LambdaQueryWrapper<TranslationTaskStep>()
                .eq(TranslationTaskStep::getTaskId, taskId));
    }

    public void resetParseStep(Long taskId) {
        TranslationTaskStep existing = stepMapper.selectOne(new LambdaQueryWrapper<TranslationTaskStep>()
                .eq(TranslationTaskStep::getTaskId, taskId)
                .eq(TranslationTaskStep::getStepCode, TaskStepCode.PARSE.name()));
        if (ObjectUtils.isEmpty(existing)) {
            insertStep(taskId, TaskStepCode.PARSE, TaskStepStatus.PENDING);
            return;
        }
        stepMapper.update(null, new LambdaUpdateWrapper<TranslationTaskStep>()
                .eq(TranslationTaskStep::getId, existing.getId())
                .set(TranslationTaskStep::getStatus, TaskStepStatus.PENDING.name())
                .set(TranslationTaskStep::getCompletedCount, 0)
                .set(TranslationTaskStep::getTotalCount, 0)
                .set(TranslationTaskStep::getRoundNo, null)
                .set(TranslationTaskStep::getSubStep, null)
                .set(TranslationTaskStep::getErrorMsg, null)
                .set(TranslationTaskStep::getStartedAt, null)
                .set(TranslationTaskStep::getFinishedAt, null));
    }

    public void initAgentSteps(Long taskId, boolean enableReview, boolean enableSummary) {
        stepMapper.delete(new LambdaQueryWrapper<TranslationTaskStep>()
                .eq(TranslationTaskStep::getTaskId, taskId)
                .in(TranslationTaskStep::getStepCode, AGENT_STEPS.stream().map(Enum::name).toList()));

        insertStep(taskId, TaskStepCode.TRANSLATE, TaskStepStatus.PENDING);

        if (enableReview) {
            insertStep(taskId, TaskStepCode.REVIEW_SCORE, TaskStepStatus.PENDING);
            insertStep(taskId, TaskStepCode.REVIEW_RETRANSLATE, TaskStepStatus.PENDING);
        } else {
            insertStep(taskId, TaskStepCode.REVIEW_SCORE, TaskStepStatus.SKIPPED);
            insertStep(taskId, TaskStepCode.REVIEW_RETRANSLATE, TaskStepStatus.SKIPPED);
        }

        if (enableSummary) {
            insertStep(taskId, TaskStepCode.SUMMARY_GUIDE, TaskStepStatus.PENDING);
            insertStep(taskId, TaskStepCode.SUMMARY_UNIFY, TaskStepStatus.PENDING);
        } else {
            insertStep(taskId, TaskStepCode.SUMMARY_GUIDE, TaskStepStatus.SKIPPED);
            insertStep(taskId, TaskStepCode.SUMMARY_UNIFY, TaskStepStatus.SKIPPED);
        }
    }

    public void startStep(Long taskId, TaskStepCode code) {
        startStep(taskId, code, 0, null, null);
    }

    public void startStep(Long taskId, TaskStepCode code, int total) {
        startStep(taskId, code, total, null, null);
    }

    public void startStep(Long taskId, TaskStepCode code, int total, Integer roundNo, String subStep) {
        LocalDateTime now = LocalDateTime.now();
        stepMapper.update(null, new LambdaUpdateWrapper<TranslationTaskStep>()
                .eq(TranslationTaskStep::getTaskId, taskId)
                .eq(TranslationTaskStep::getStepCode, code.name())
                .set(TranslationTaskStep::getStatus, TaskStepStatus.RUNNING.name())
                .set(TranslationTaskStep::getCompletedCount, 0)
                .set(TranslationTaskStep::getTotalCount, Math.max(total, 0))
                .set(TranslationTaskStep::getRoundNo, roundNo)
                .set(TranslationTaskStep::getSubStep, subStep)
                .set(TranslationTaskStep::getErrorMsg, null)
                .set(TranslationTaskStep::getStartedAt, now)
                .set(TranslationTaskStep::getFinishedAt, null));
    }

    public void updateProgress(Long taskId, TaskStepCode code, int completed, int total) {
        stepMapper.update(null, new LambdaUpdateWrapper<TranslationTaskStep>()
                .eq(TranslationTaskStep::getTaskId, taskId)
                .eq(TranslationTaskStep::getStepCode, code.name())
                .set(TranslationTaskStep::getCompletedCount, completed)
                .set(TranslationTaskStep::getTotalCount, total));
    }

    public void updateSubStep(Long taskId, TaskStepCode code, String subStep, Integer roundNo) {
        LambdaUpdateWrapper<TranslationTaskStep> wrapper = new LambdaUpdateWrapper<TranslationTaskStep>()
                .eq(TranslationTaskStep::getTaskId, taskId)
                .eq(TranslationTaskStep::getStepCode, code.name());
        if (!ObjectUtils.isEmpty(subStep)) {
            wrapper.set(TranslationTaskStep::getSubStep, subStep);
        }
        if (!ObjectUtils.isEmpty(roundNo)) {
            wrapper.set(TranslationTaskStep::getRoundNo, roundNo);
        }
        stepMapper.update(null, wrapper);
    }

    public void completeStep(Long taskId, TaskStepCode code) {
        TranslationTaskStep step = stepMapper.selectOne(new LambdaQueryWrapper<TranslationTaskStep>()
                .eq(TranslationTaskStep::getTaskId, taskId)
                .eq(TranslationTaskStep::getStepCode, code.name()));
        int completed = ObjectUtils.isEmpty(step) ? 0 : step.getCompletedCount();
        int total = ObjectUtils.isEmpty(step) ? 0 : step.getTotalCount();
        if (total > 0 && completed < total) {
            completed = total;
        }
        stepMapper.update(null, new LambdaUpdateWrapper<TranslationTaskStep>()
                .eq(TranslationTaskStep::getTaskId, taskId)
                .eq(TranslationTaskStep::getStepCode, code.name())
                .set(TranslationTaskStep::getStatus, TaskStepStatus.DONE.name())
                .set(TranslationTaskStep::getCompletedCount, completed)
                .set(TranslationTaskStep::getFinishedAt, LocalDateTime.now()));
    }

    public void skipStep(Long taskId, TaskStepCode code) {
        stepMapper.update(null, new LambdaUpdateWrapper<TranslationTaskStep>()
                .eq(TranslationTaskStep::getTaskId, taskId)
                .eq(TranslationTaskStep::getStepCode, code.name())
                .set(TranslationTaskStep::getStatus, TaskStepStatus.SKIPPED.name())
                .set(TranslationTaskStep::getFinishedAt, LocalDateTime.now()));
    }

    public void failStep(Long taskId, TaskStepCode code, String errorMsg) {
        stepMapper.update(null, new LambdaUpdateWrapper<TranslationTaskStep>()
                .eq(TranslationTaskStep::getTaskId, taskId)
                .eq(TranslationTaskStep::getStepCode, code.name())
                .set(TranslationTaskStep::getStatus, TaskStepStatus.FAILED.name())
                .set(TranslationTaskStep::getErrorMsg, errorMsg)
                .set(TranslationTaskStep::getFinishedAt, LocalDateTime.now()));
    }

    public void failRunningStep(Long taskId, String errorMsg) {
        stepMapper.update(null, new LambdaUpdateWrapper<TranslationTaskStep>()
                .eq(TranslationTaskStep::getTaskId, taskId)
                .eq(TranslationTaskStep::getStatus, TaskStepStatus.RUNNING.name())
                .set(TranslationTaskStep::getStatus, TaskStepStatus.FAILED.name())
                .set(TranslationTaskStep::getErrorMsg, errorMsg)
                .set(TranslationTaskStep::getFinishedAt, LocalDateTime.now()));
    }

    private void insertStep(Long taskId, TaskStepCode code, TaskStepStatus status) {
        TranslationTaskStep step = new TranslationTaskStep();
        step.setTaskId(taskId);
        step.setStepCode(code.name());
        step.setOrderNo(code.orderNo());
        step.setStatus(status.name());
        step.setCompletedCount(0);
        step.setTotalCount(0);
        stepMapper.insert(step);
    }
}
