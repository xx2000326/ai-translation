-- ============================================================
-- 升级脚本：translation_task_step 任务步骤进度子表
-- 日期：2026-06-08
-- 说明：子表记录解析/翻译/审校/风格统一等步骤状态与进度
--       步骤数据由应用写入，本脚本不含存量任务回填
-- 执行前请备份。已有库按时间顺序执行。
-- ============================================================

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

-- translation_task：进度改由子表维护，删除冗余列
ALTER TABLE translation_task
    DROP COLUMN total_sentences,
    DROP COLUMN completed_sentences,
    DROP COLUMN progress_phase,
    DROP COLUMN review_sub_phase;
