-- ============================================================
-- AI 翻译助手 - 文档拆分引擎结果表（独立于翻译解析相关表）
-- 模块：com.xx.aitranslation.service.chunk
-- 数据库：ai_translation
-- 说明：拆分引擎产出的 Chunk 落库存储，供后续翻译 / RAG / 摘要等场景复用。
-- ============================================================

CREATE TABLE IF NOT EXISTS document_chunk (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    source_id BIGINT COMMENT '来源标识（调用方指定，如任务ID / 业务文档ID）',
    source_name VARCHAR(255) COMMENT '源文件名',
    file_type VARCHAR(20) COMMENT '文件类型（ChunkFileType.name()）',
    strategy VARCHAR(20) COMMENT '采用的拆分策略（ChunkStrategyType.name()）',
    chunk_key VARCHAR(64) COMMENT '文档内块唯一标识（如 L1-0）',
    parent_key VARCHAR(64) COMMENT '父块标识（根级为空）',
    level INT COMMENT '层级：0=Root / 1=Parent / 2=Child',
    order_no INT COMMENT '同层级顺序号',
    title VARCHAR(512) COMMENT '块标题（Markdown 等策略可用）',
    content MEDIUMTEXT COMMENT '块内容',
    token_count INT COMMENT '估算 token 数',
    char_count INT COMMENT '字符数',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_source_id (source_id),
    INDEX idx_source_level (source_id, level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档拆分结果块表';
