package com.xx.aitranslation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务级术语实体，对应 MySQL task_glossary 表。
 * <p>
 * 与客户级术语库 {@code glossary} 区分：用于本次任务临时生效的术语约束。
 */
@Data
@TableName("task_glossary")
public class TaskGlossary {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属任务ID */
    private Long taskId;

    /** 原文术语 */
    private String term;

    /** 术语译文 */
    private String translation;

    private LocalDateTime createTime;
}
