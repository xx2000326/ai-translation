-- ============================================================
-- 升级脚本：translation_task 增加 parse_granularity 字段
-- 用途：解析时支持选择拆分粒度（SENTENCE 按句 / PARAGRAPH 按段）
-- 执行前请备份。已有库按时间顺序执行。
-- ============================================================

ALTER TABLE translation_task
    ADD COLUMN parse_granularity VARCHAR(20) DEFAULT 'SENTENCE' COMMENT '解析拆分粒度（SENTENCE 按句 / PARAGRAPH 按段）'
    AFTER target_lang;
