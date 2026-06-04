package com.xx.aitranslation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 术语库实体，对应 MySQL glossary 表，用于增强翻译一致性。
 */
@Data
@TableName("glossary")
public class Glossary {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属客户ID（术语库按客户隔离） */
    private Long customerId;

    /** 原文术语 */
    private String term;

    /** 术语译文 */
    private String translation;

    /** 分类（如：法律 / 医疗 / IT 等） */
    private String category;

    private LocalDateTime createTime;
}
