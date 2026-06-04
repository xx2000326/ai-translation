package com.xx.aitranslation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 翻译项目实体，对应 MySQL project 表。
 * <p>
 * 项目用于承载后续的文件翻译场景：关联客户、是否启用术语库、默认翻译角色 / 风格等配置。
 */
@Data
@TableName("project")
public class Project {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 项目名称 */
    private String name;

    /** 关联客户ID */
    private Long customerId;

    /** 是否启用术语库（启用后翻译会注入该客户的术语规则） */
    private Boolean enableGlossary;

    /** 默认翻译角色 code */
    private String role;

    /** 默认翻译风格 code */
    private String style;

    /** 项目描述 */
    private String description;

    private LocalDateTime createTime;
}
