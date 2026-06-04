package com.xx.aitranslation.dto;

import lombok.Data;

/**
 * 段落人工二次修改请求体：批量更新段落原文。
 */
@Data
public class SegmentUpdateRequest {

    /** 段落ID */
    private Long id;

    /** 修改后的原文 */
    private String originalText;
}
