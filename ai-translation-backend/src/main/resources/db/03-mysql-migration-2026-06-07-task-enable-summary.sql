-- ============================================================
-- 升级脚本：translation_task 增加 enable_summary 字段
-- 用途：V1 模块七「汇总 Agent / 全文风格统一」开关
-- 执行前请备份。已有库按时间顺序执行。
-- ============================================================

ALTER TABLE translation_task
    ADD COLUMN enable_summary TINYINT(1) DEFAULT 0 COMMENT '是否启用全文风格统一（汇总 Agent）'
    AFTER review_model;
