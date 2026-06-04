package com.xx.aitranslation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客户表实体，对应 MySQL customer 表。
 */
@Data
@TableName("customer")
public class Customer {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 客户名称 */
    private String name;

    /** 联系方式 */
    private String contact;

    /** 默认翻译角色，对应 {@link com.xx.aitranslation.enums.TranslationRole} 的 code */
    private String defaultRole;

    /** 默认翻译风格，对应 {@link com.xx.aitranslation.enums.TranslationStyle} 的 code */
    private String defaultStyle;

    /** 备注 / 客户要求 */
    private String remark;

    private LocalDateTime createTime;
}
