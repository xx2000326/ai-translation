package com.xx.aitranslation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 翻译任务步骤进度实体，对应 MySQL translation_task_step 表。
 */
@Data
@TableName("translation_task_step")
public class TranslationTaskStep {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    /** 步骤编码，存 {@link com.xx.aitranslation.enums.TaskStepCode} 的 name() */
    private String stepCode;

    private Integer orderNo;

    /** 步骤状态，存 {@link com.xx.aitranslation.enums.TaskStepStatus} 的 name() */
    private String status;

    private Integer completedCount;

    private Integer totalCount;

    private Integer roundNo;

    private String subStep;

    private String errorMsg;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
