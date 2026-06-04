package com.xx.aitranslation.enums;

/**
 * 翻译任务状态机：覆盖从草稿、文件上传、解析、翻译、审校到导出/完成的全生命周期。
 * <p>
 * 数据库以 {@link #name()} 字符串形式存储于 {@code translation_task.status}。
 */
public enum TaskStatus {

    /** 草稿（任务已创建，尚未上传文件） */
    DRAFT,
    /** 文件已上传 */
    FILE_UPLOADED,
    /** 解析中 */
    PARSING,
    /** 解析完成（已切分段落） */
    PARSED,
    /** 翻译中 */
    TRANSLATING,
    /** 翻译完成 */
    TRANSLATED,
    /** 审校中 */
    REVIEWING,
    /** 审校完成 */
    REVIEW_DONE,
    /** 需人工复核 */
    MANUAL_REVIEW,
    /** 已完成 */
    COMPLETED,
    /** 已导出 */
    EXPORTED,
    /** 失败 */
    FAILED
}
