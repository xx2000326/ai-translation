-- ============================================================
-- 升级脚本：translation_paragraph 增加章节聚合字段
-- 用途：TITLE 策略章节内 Part 拆分后，按 section_id 归并同一章节（情绪分析等）
-- 执行前请备份。已有库按时间顺序执行。
-- ============================================================

ALTER TABLE translation_paragraph
    ADD COLUMN section_id VARCHAR(64) COMMENT '章节节点 ID（Part 块共享，聚合键）' AFTER parent_title,
    ADD COLUMN section_title VARCHAR(512) COMMENT '原章节标题（不含 -PartN）' AFTER section_id;
