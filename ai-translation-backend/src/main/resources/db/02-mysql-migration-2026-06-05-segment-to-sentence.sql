-- ============================================================
-- 迁移脚本：translation_segment → document / paragraph / sentence
-- 适用：已存在旧版 translation_segment 表的环境
-- 数据库：ai_translation
-- 注意：会删除 translation_segment 及其全部数据，执行前请备份
-- ============================================================

USE ai_translation;

-- 1. 删除旧段落表
DROP TABLE IF EXISTS translation_segment;

-- 2. 解析文档（文件）表：1 任务 1 文档
CREATE TABLE IF NOT EXISTS translation_document (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id BIGINT NOT NULL COMMENT '所属任务ID',
    file_name VARCHAR(255) COMMENT '源文件名称',
    file_key VARCHAR(255) COMMENT '源文件存储 key',
    file_type VARCHAR(20) COMMENT 'FileType.name()',
    paragraph_count INT DEFAULT 0 COMMENT '段落数',
    sentence_count INT DEFAULT 0 COMMENT '句子数',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_task_id (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='解析文档（文件）表';

-- 3. 段落表：Okapi TextUnit / Word 段落
CREATE TABLE IF NOT EXISTS translation_paragraph (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    document_id BIGINT NOT NULL COMMENT '所属文档ID',
    order_no INT NOT NULL COMMENT '段落顺序号',
    para_position VARCHAR(128) COMMENT '解析器定位（Okapi tu id 等）',
    para_type VARCHAR(50) COMMENT '段落类型',
    original_text MEDIUMTEXT COMMENT '段落原文（句子拼接）',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_document_id (document_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='翻译段落表';

-- 4. 句子表：SRX 分句，最小翻译单元
CREATE TABLE IF NOT EXISTS translation_sentence (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    paragraph_id BIGINT NOT NULL COMMENT '所属段落ID',
    order_no INT NOT NULL COMMENT '文档内全局顺序号',
    sent_index INT NOT NULL COMMENT '段内句子序号',
    sent_position VARCHAR(128) COMMENT '句子定位',
    original_text MEDIUMTEXT COMMENT '原文',
    translated_text MEDIUMTEXT COMMENT '机翻译文',
    reviewed_text MEDIUMTEXT COMMENT '审校后译文',
    final_text MEDIUMTEXT COMMENT '最终采用译文',
    review_score INT COMMENT '审校评分',
    review_advice MEDIUMTEXT COMMENT '审校建议',
    review_flag TINYINT(1) DEFAULT 0 COMMENT '是否需人工复核',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_paragraph_id (paragraph_id),
    INDEX idx_order_no (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='翻译句子表';
