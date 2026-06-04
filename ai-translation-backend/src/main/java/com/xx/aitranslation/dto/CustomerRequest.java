package com.xx.aitranslation.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 客户新增 / 更新请求体。
 */
@Data
public class CustomerRequest {

    @NotBlank(message = "customer.name.empty")
    private String name;

    /** 联系方式 */
    private String contact;

    /** 默认翻译角色 code */
    private String defaultRole;

    /** 默认翻译风格 code */
    private String defaultStyle;

    /** 备注 / 客户要求 */
    private String remark;
}
