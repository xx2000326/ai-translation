-- ============================================================
-- 升级脚本：translation_paragraph 增加高级拆分（结构化 Chunk）字段
-- 用途：解析粒度 STRUCTURE（高级拆分）下保留章节层级与父子关系
--   chunk_id     Chunk 唯一标识（文档内，如 chunk-0001）
--   title        章节标题（或超长切分后的 标题-PartN）
--   parent_title 父标题（层级父章节标题）
--   node_level   章节层级（第X章=1 / 第X节=2 / 1.1=2 ...，根级正文=0）
-- 执行前请备份。已有库按时间顺序执行。
-- ============================================================

ALTER TABLE translation_paragraph
    ADD COLUMN chunk_id VARCHAR(64) COMMENT '高级拆分 Chunk 唯一标识（文档内）' AFTER original_text,
    ADD COLUMN title VARCHAR(512) COMMENT '高级拆分章节标题（或 标题-PartN）' AFTER chunk_id,
    ADD COLUMN parent_title VARCHAR(512) COMMENT '高级拆分父标题（层级父章节标题）' AFTER title,
    ADD COLUMN node_level INT COMMENT '高级拆分章节层级（根级正文=0）' AFTER parent_title;
