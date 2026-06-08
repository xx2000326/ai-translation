package com.xx.aitranslation.enums;

/**
 * 审校子阶段，与 {@code translation_task.review_sub_phase} 对应。
 */
public enum ReviewSubPhase {

    /** 逐句评分 */
    SCORING,

    /** 未达标句重翻 */
    RETRANSLATE
}
