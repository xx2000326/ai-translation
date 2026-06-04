package com.xx.aitranslation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 翻译历史实体，对应 MySQL translation_history 表。
 */
@Data
@TableName("translation_history")
public class TranslationHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long customerId;

    private String originalText;

    private String translatedText;

    /** 翻译角色 code */
    private String role;

    /** 翻译风格 code */
    private String style;

    private LocalDateTime createTime;
}
