package com.xx.aitranslation.dto;

import com.xx.aitranslation.entity.TranslationHistory;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 翻译历史展示 VO：在历史全字段基础上附带所属客户名称。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TranslationHistoryVO extends TranslationHistory {

    /** 所属客户名称 */
    private String customerName;
}
