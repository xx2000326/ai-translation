-- ============================================================
-- 升级脚本：translation_task 增加文档拆分引擎配置字段
-- 用途：工作流解析阶段改用通用文档拆分引擎（com.xx.aitranslation.service.chunk），
--      由任务配置选择拆分策略与参数，替代旧的 parse_granularity（按句/按段/结构）。
-- 说明：parse_granularity 字段保留以兼容历史数据，新流程不再使用。
-- 执行前请备份。已有库按时间顺序执行。
-- ============================================================

ALTER TABLE translation_task
    ADD COLUMN chunk_strategy VARCHAR(20) DEFAULT 'AUTO'
        COMMENT '文档拆分策略（ChunkStrategyType.name()，AUTO 表示按文件类型自动选择）' AFTER parse_granularity,
    ADD COLUMN chunk_size INT DEFAULT 1000 COMMENT '固定长度策略单块字符数' AFTER chunk_strategy,
    ADD COLUMN chunk_overlap INT DEFAULT 100 COMMENT '相邻块重叠字符数' AFTER chunk_size,
    ADD COLUMN chunk_parent_size INT DEFAULT 5000 COMMENT '层级策略父块字符数' AFTER chunk_overlap,
    ADD COLUMN chunk_child_size INT DEFAULT 1000 COMMENT '层级策略子块字符数' AFTER chunk_parent_size;
