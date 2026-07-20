package com.xx.aitranslation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("translation_image")
public class TranslationImage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    private Long documentId;

    private String pythonTaskId;

    private String blockId;

    private Integer pageNo;

    private Integer orderNo;

    private String bboxJson;

    private String originalFileKey;

    private String translatedFileKey;

    private String mimeType;

    private Integer width;

    private Integer height;

    private String status;

    private String errorMsg;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
