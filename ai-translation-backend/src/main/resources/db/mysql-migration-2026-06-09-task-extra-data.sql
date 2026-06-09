-- ============================================================
-- 升级脚本：translation_task 增加 extra_data 扩展配置
-- 用途：Markdown 标题深度等策略参数（JSON）
-- 执行前请备份。已有库按时间顺序执行。
-- ============================================================

ALTER TABLE translation_task
    ADD COLUMN extra_data JSON COMMENT '任务扩展配置（策略参数等）' AFTER chunk_child_size;
