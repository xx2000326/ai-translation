-- ============================================================
-- AI 翻译助手 - MySQL 业务库 DDL（参考脚本，需手动执行）
-- 数据库：ai_translation
-- ============================================================

CREATE TABLE IF NOT EXISTS customer (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(50) UNIQUE NOT NULL COMMENT '客户名称',
    contact VARCHAR(100) COMMENT '联系方式',
    default_role VARCHAR(50) COMMENT '默认翻译角色',
    default_style VARCHAR(50) COMMENT '默认翻译风格',
    remark VARCHAR(500) COMMENT '备注 / 客户要求',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户表';

CREATE TABLE IF NOT EXISTS translation_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_id BIGINT COMMENT '客户ID',
    original_text TEXT COMMENT '原文',
    translated_text TEXT COMMENT '译文',
    role VARCHAR(50) COMMENT '翻译角色',
    style VARCHAR(50) COMMENT '翻译风格',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='翻译历史表';

CREATE TABLE IF NOT EXISTS project (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL COMMENT '项目名称',
    customer_id BIGINT COMMENT '关联客户ID',
    enable_glossary TINYINT(1) DEFAULT 0 COMMENT '是否启用术语库',
    role VARCHAR(50) COMMENT '默认翻译角色',
    style VARCHAR(50) COMMENT '默认翻译风格',
    description VARCHAR(500) COMMENT '项目描述',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_customer_id (customer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='翻译项目表';

CREATE TABLE IF NOT EXISTS glossary (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_id BIGINT COMMENT '所属客户ID',
    term VARCHAR(255) COMMENT '原文术语',
    translation VARCHAR(255) COMMENT '术语译文',
    category VARCHAR(50) COMMENT '分类',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_customer_id (customer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='术语库表';

-- ============================================================
-- 文件翻译任务相关表（任务 / 文档 / 段落 / 句子 / 任务级术语）
-- 解析结构：translation_document → translation_paragraph → translation_sentence
-- 旧版 translation_segment 已废弃，升级请执行：
--   mysql-migration-2026-06-05-segment-to-sentence.sql
-- ============================================================

CREATE TABLE IF NOT EXISTS translation_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT COMMENT '关联项目ID',
    customer_id BIGINT COMMENT '关联客户ID',
    status VARCHAR(20) COMMENT '任务状态（TaskStatus.name()）',
    requirement MEDIUMTEXT COMMENT '翻译要求',
    description MEDIUMTEXT COMMENT '任务描述',
    source_lang VARCHAR(20) COMMENT '源语言 code',
    target_lang VARCHAR(20) COMMENT '目标语言 code',
    parse_granularity VARCHAR(20) DEFAULT 'SENTENCE' COMMENT '（已废弃，保留兼容）解析拆分粒度',
    chunk_strategy VARCHAR(20) DEFAULT 'AUTO' COMMENT '文档拆分策略（ChunkStrategyType.name()，AUTO 自动）',
    chunk_size INT DEFAULT 1000 COMMENT '固定长度策略单块字符数',
    chunk_overlap INT DEFAULT 100 COMMENT '相邻块重叠字符数',
    chunk_parent_size INT DEFAULT 5000 COMMENT '层级策略父块字符数',
    chunk_child_size INT DEFAULT 1000 COMMENT '层级策略子块字符数',
    enable_glossary TINYINT(1) DEFAULT 0 COMMENT '是否启用术语库',
    enable_history TINYINT(1) DEFAULT 0 COMMENT '是否启用历史/RAG 记忆',
    translate_model VARCHAR(50) COMMENT '翻译模型 code',
    enable_review TINYINT(1) DEFAULT 0 COMMENT '是否启用 AI 审校',
    review_model VARCHAR(50) COMMENT '审校模型 code',
    enable_summary TINYINT(1) DEFAULT 0 COMMENT '是否启用全文风格统一（汇总 Agent）',
    review_score INT COMMENT '审校综合评分',
    review_round INT COMMENT '审校轮次',
    source_file_name VARCHAR(255) COMMENT '源文件原始名称',
    source_file_key VARCHAR(255) COMMENT '源文件存储 key',
    source_file_type VARCHAR(20) COMMENT '源文件类型（ChunkFileType.name()）',
    error_msg MEDIUMTEXT COMMENT '失败原因',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_project_id (project_id),
    INDEX idx_customer_id (customer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='翻译任务表';

CREATE TABLE IF NOT EXISTS translation_task_step (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id         BIGINT NOT NULL COMMENT '所属任务ID',
    step_code       VARCHAR(32) NOT NULL COMMENT '步骤编码 TaskStepCode.name()',
    order_no        INT NOT NULL COMMENT '展示与逻辑顺序',
    status          VARCHAR(16) NOT NULL DEFAULT 'PENDING'
                    COMMENT 'PENDING/RUNNING/DONE/SKIPPED/FAILED',
    completed_count INT NOT NULL DEFAULT 0 COMMENT '已完成计数',
    total_count     INT NOT NULL DEFAULT 0 COMMENT '总计数',
    round_no        INT COMMENT '轮次（审校等多轮步骤）',
    sub_step        VARCHAR(32) COMMENT '子步骤编码',
    error_msg       MEDIUMTEXT COMMENT '失败原因',
    started_at      TIMESTAMP NULL,
    finished_at     TIMESTAMP NULL,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_task_step (task_id, step_code),
    INDEX idx_task_id (task_id),
    INDEX idx_task_status (task_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='翻译任务步骤进度';

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

CREATE TABLE IF NOT EXISTS translation_paragraph (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    document_id BIGINT NOT NULL COMMENT '所属文档ID',
    order_no INT NOT NULL COMMENT '段落顺序号',
    para_position VARCHAR(128) COMMENT '解析器定位（Okapi tu id 等）',
    para_type VARCHAR(50) COMMENT '段落类型（paragraph / chunk）',
    original_text MEDIUMTEXT COMMENT '段落原文（句子拼接）',
    chunk_id VARCHAR(64) COMMENT '高级拆分 Chunk 唯一标识（文档内）',
    title VARCHAR(512) COMMENT '高级拆分章节标题（或 标题-PartN）',
    parent_title VARCHAR(512) COMMENT '高级拆分父标题（层级父章节标题）',
    section_id VARCHAR(64) COMMENT '章节节点 ID（Part 块共享，聚合键）',
    section_title VARCHAR(512) COMMENT '原章节标题（不含 -PartN）',
    node_level INT COMMENT '高级拆分章节层级（根级正文=0）',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_document_id (document_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='翻译段落表';

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

CREATE TABLE IF NOT EXISTS task_glossary (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id BIGINT COMMENT '所属任务ID',
    term VARCHAR(255) COMMENT '原文术语',
    translation VARCHAR(255) COMMENT '术语译文',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_task_id (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务级术语表';
