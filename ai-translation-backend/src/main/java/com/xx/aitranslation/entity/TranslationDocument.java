package com.xx.aitranslation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("translation_document")
public class TranslationDocument {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    private String fileName;

    private String fileKey;

    private String fileType;

    private Integer paragraphCount;

    private Integer sentenceCount;

    private LocalDateTime createTime;
}
