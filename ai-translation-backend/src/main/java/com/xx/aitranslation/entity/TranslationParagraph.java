package com.xx.aitranslation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("translation_paragraph")
public class TranslationParagraph {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long documentId;

    private Integer orderNo;

    private String paraPosition;

    private String paraType;

    private String originalText;

    private LocalDateTime createTime;
}
