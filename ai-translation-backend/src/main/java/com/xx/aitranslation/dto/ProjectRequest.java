package com.xx.aitranslation.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 项目新增 / 更新请求体。
 */
@Data
public class ProjectRequest {

    @NotBlank(message = "project.name.empty")
    private String name;

    /** 关联客户ID */
    private Long customerId;

    /** 是否启用术语库 */
    private Boolean enableGlossary;

    /** 默认翻译角色 code */
    private String role;

    /** 默认翻译风格 code */
    private String style;

    private String description;
}
