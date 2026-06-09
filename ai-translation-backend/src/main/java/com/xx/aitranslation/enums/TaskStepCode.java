package com.xx.aitranslation.enums;

/**
 * 翻译任务步骤编码，与 {@code translation_task_step.step_code} 对应。
 */
public enum TaskStepCode {

    /** 文档解析 */
    PARSE(10),

    /** 并发初翻 */
    TRANSLATE(20),

    /** 审校逐句评分 */
    REVIEW_SCORE(30),

    /** 审校未达标句重翻 */
    REVIEW_RETRANSLATE(40),

    /** 风格指南抽取（Phase 1，无句级进度） */
    SUMMARY_GUIDE(50),

    /** 风格统一分批润色（Phase 2） */
    SUMMARY_UNIFY(60);

    private final int orderNo;

    TaskStepCode(int orderNo) {
        this.orderNo = orderNo;
    }

    public int orderNo() {
        return orderNo;
    }
}
