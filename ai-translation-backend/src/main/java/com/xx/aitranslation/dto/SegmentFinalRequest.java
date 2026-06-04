package com.xx.aitranslation.dto;

import lombok.Data;

/**
 * 段落最终译文保存请求体：人工审校确认后提交的最终文本。
 */
@Data
public class SegmentFinalRequest {

    /** 最终采用译文 */
    private String finalText;
}
