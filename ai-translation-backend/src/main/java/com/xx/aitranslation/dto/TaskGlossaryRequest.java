package com.xx.aitranslation.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 任务级临时术语新增请求体。
 */
@Data
public class TaskGlossaryRequest {

    @NotBlank(message = "glossary.term.empty")
    private String term;

    @NotBlank(message = "glossary.translation.empty")
    private String translation;
}
