package com.xx.aitranslation.dto;

import lombok.Data;

/**
 * 启动翻译请求体：手动选择翻译模型，并可选开启 AI 审校与指定审校模型。
 * <p>
 * 字段均为可选，服务层仅对非空字段覆盖任务已有配置。
 */
@Data
public class StartTranslateRequest {

    /** 翻译使用的模型 code（如 qwen-plus / deepseek-chat） */
    private String model;

    /** 是否启用 AI 审校 */
    private Boolean enableReview;

    /** 审校使用的模型 code */
    private String reviewModel;
}
