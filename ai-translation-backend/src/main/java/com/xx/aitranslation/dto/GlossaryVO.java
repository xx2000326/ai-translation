package com.xx.aitranslation.dto;

import com.xx.aitranslation.entity.Glossary;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 术语库展示 VO：在术语全字段基础上附带所属客户名称。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class GlossaryVO extends Glossary {

    /** 所属客户名称 */
    private String customerName;
}
