package com.xx.aitranslation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("translation_sentence")
public class TranslationSentence {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long paragraphId;

    /** 文档内全局顺序，供审校/导出对齐 */
    private Integer orderNo;

    private Integer sentIndex;

    private String sentPosition;

    private String originalText;

    private String translatedText;

    private String reviewedText;

    private String finalText;

    private Integer reviewScore;

    private String reviewAdvice;

    private Boolean reviewFlag;

    private LocalDateTime createTime;
}
