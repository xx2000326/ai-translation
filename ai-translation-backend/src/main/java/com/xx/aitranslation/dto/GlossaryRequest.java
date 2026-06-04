package com.xx.aitranslation.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 新增术语请求体，对应 POST /api/glossary。
 */
@Data
public class GlossaryRequest {

    /** 所属客户ID（术语库按客户隔离） */
    private Long customerId;

    @NotBlank(message = "glossary.term.empty")
    private String term;

    @NotBlank(message = "glossary.translation.empty")
    private String translation;

    private String category;
}
