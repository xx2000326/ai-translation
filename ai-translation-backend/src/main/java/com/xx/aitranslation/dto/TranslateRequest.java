package com.xx.aitranslation.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 翻译请求体，对应 POST /api/translate。
 */
@Data
public class TranslateRequest {

    private Long customerId;

    @NotBlank(message = "translate.text.empty")
    private String text;

    /** 翻译角色 code，可空（为空时回退到客户默认或 professional） */
    private String role;

    /** 翻译风格 code，可空（为空时回退到客户默认或 formal） */
    private String style;
}
