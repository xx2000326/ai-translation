package com.xx.aitranslation.dto;

import com.xx.aitranslation.entity.TranslationTaskStep;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TaskStepDto {

    private String stepCode;
    private Integer orderNo;
    private String status;
    private Integer completedCount;
    private Integer totalCount;
    private Integer roundNo;
    private String subStep;
    private String errorMsg;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;

    public static TaskStepDto from(TranslationTaskStep step) {
        TaskStepDto dto = new TaskStepDto();
        dto.setStepCode(step.getStepCode());
        dto.setOrderNo(step.getOrderNo());
        dto.setStatus(step.getStatus());
        dto.setCompletedCount(step.getCompletedCount());
        dto.setTotalCount(step.getTotalCount());
        dto.setRoundNo(step.getRoundNo());
        dto.setSubStep(step.getSubStep());
        dto.setErrorMsg(step.getErrorMsg());
        dto.setStartedAt(step.getStartedAt());
        dto.setFinishedAt(step.getFinishedAt());
        return dto;
    }
}
