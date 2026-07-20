-- ============================================================
-- 升级脚本：PDF 图片解析与图片翻译记录
-- 日期：2026-06-21
-- 说明：
--   1. translation_task 增加 PDF 图片翻译开关和图片模型配置。
--   2. translation_image 记录 PDF 图片位置、原图 key、译图 key 和处理状态。
-- 执行前请备份。已有库按时间顺序执行。
-- ============================================================

ALTER TABLE translation_task
    ADD COLUMN enable_image_translation TINYINT(1) DEFAULT 0 COMMENT '是否翻译 PDF 图片' AFTER enable_summary,
    ADD COLUMN image_translation_model VARCHAR(100) COMMENT 'PDF 图片翻译模型 code' AFTER enable_image_translation;

CREATE TABLE IF NOT EXISTS translation_image (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id BIGINT NOT NULL COMMENT '所属任务ID',
    document_id BIGINT COMMENT '所属解析文档ID',
    python_task_id VARCHAR(64) COMMENT 'Python PDF 解析任务ID',
    block_id VARCHAR(64) COMMENT 'Python 解析块ID',
    page_no INT COMMENT 'PDF 页码，从 1 开始',
    order_no INT COMMENT '图片在文档内顺序',
    bbox_json JSON COMMENT '图片位置 [x0,y0,x1,y1]',
    original_file_key VARCHAR(255) COMMENT '原图文件 key',
    translated_file_key VARCHAR(255) COMMENT '译后图片文件 key',
    mime_type VARCHAR(64) COMMENT '图片 MIME 类型',
    width INT COMMENT '图片宽度',
    height INT COMMENT '图片高度',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SKIPPED/TRANSLATING/TRANSLATED/FAILED',
    error_msg MEDIUMTEXT COMMENT '图片翻译失败原因',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_task_id (task_id),
    INDEX idx_document_id (document_id),
    INDEX idx_task_page_order (task_id, page_no, order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='PDF 图片解析与翻译记录表';
