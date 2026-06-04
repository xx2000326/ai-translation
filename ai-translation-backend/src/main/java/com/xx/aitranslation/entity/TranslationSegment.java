package com.xx.aitranslation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 翻译段落实体，对应 MySQL translation_segment 表。
 * <p>
 * 源文件解析后按段落切分，每个段落独立翻译与审校，便于并发与人工复核。
 */
@Data
@TableName("translation_segment")
public class TranslationSegment {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属任务ID */
    private Long taskId;

    /** 段落顺序号（从 0 / 1 递增，用于还原原文顺序） */
    private Integer orderNo;

    /** 块类型（如 paragraph / heading / html 标签等，便于导出还原结构） */
    private String blockType;

    /** 原文 */
    private String originalText;

    /** 机翻译文 */
    private String translatedText;

    /** 审校后译文 */
    private String reviewedText;

    /** 最终采用译文 */
    private String finalText;

    /** 段落审校评分 */
    private Integer reviewScore;

    /** 审校建议 */
    private String reviewAdvice;

    /** 是否需人工复核 */
    private Boolean reviewFlag;

    private LocalDateTime createTime;
}
