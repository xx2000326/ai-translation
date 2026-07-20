package com.xx.aitranslation.dto;

import lombok.Data;

@Data
public class TranslationImageResponse {
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
}
