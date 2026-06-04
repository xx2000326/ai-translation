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
-- 文件翻译任务相关表（任务 / 段落 / 任务级术语）
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
    enable_glossary TINYINT(1) DEFAULT 0 COMMENT '是否启用术语库',
    enable_history TINYINT(1) DEFAULT 0 COMMENT '是否启用历史/RAG 记忆',
    translate_model VARCHAR(50) COMMENT '翻译模型 code',
    enable_review TINYINT(1) DEFAULT 0 COMMENT '是否启用 AI 审校',
    review_model VARCHAR(50) COMMENT '审校模型 code',
    review_score INT COMMENT '审校综合评分',
    review_round INT COMMENT '审校轮次',
    source_file_name VARCHAR(255) COMMENT '源文件原始名称',
    source_file_key VARCHAR(255) COMMENT '源文件存储 key',
    source_file_type VARCHAR(20) COMMENT '源文件类型（FileType.name()）',
    error_msg MEDIUMTEXT COMMENT '失败原因',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_project_id (project_id),
    INDEX idx_customer_id (customer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='翻译任务表';

CREATE TABLE IF NOT EXISTS translation_segment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id BIGINT COMMENT '所属任务ID',
    order_no INT COMMENT '段落顺序号',
    block_type VARCHAR(50) COMMENT '块类型',
    original_text MEDIUMTEXT COMMENT '原文',
    translated_text MEDIUMTEXT COMMENT '机翻译文',
    reviewed_text MEDIUMTEXT COMMENT '审校后译文',
    final_text MEDIUMTEXT COMMENT '最终采用译文',
    review_score INT COMMENT '段落审校评分',
    review_advice MEDIUMTEXT COMMENT '审校建议',
    review_flag TINYINT(1) DEFAULT 0 COMMENT '是否需人工复核',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_task_id (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='翻译段落表';

CREATE TABLE IF NOT EXISTS task_glossary (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id BIGINT COMMENT '所属任务ID',
    term VARCHAR(255) COMMENT '原文术语',
    translation VARCHAR(255) COMMENT '术语译文',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_task_id (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务级术语表';
