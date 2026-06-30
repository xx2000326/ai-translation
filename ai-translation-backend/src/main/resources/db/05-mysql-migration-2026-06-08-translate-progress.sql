-- ============================================================
-- 升级脚本：translation_task 增加翻译/审校进度字段
-- 日期：2026-06-08
-- 说明：支持句子级队列翻译进度、审校阶段与子阶段(SCORING/RETRANSLATE)展示
-- 执行前请备份。已有库按时间顺序执行。
-- ============================================================

ALTER TABLE translation_task
    ADD COLUMN total_sentences INT NOT NULL DEFAULT 0 COMMENT '待翻译句子总数' AFTER review_round,
    ADD COLUMN completed_sentences INT NOT NULL DEFAULT 0 COMMENT '已完成翻译句子数' AFTER total_sentences,
    ADD COLUMN progress_phase VARCHAR(20) NOT NULL DEFAULT 'TRANSLATE'
        COMMENT '进度阶段（TRANSLATE 初翻 / REVIEW 审校）' AFTER completed_sentences,
    ADD COLUMN review_sub_phase VARCHAR(20) NULL
        COMMENT '审校子阶段（SCORING 评分 / RETRANSLATE 重翻）' AFTER progress_phase;
