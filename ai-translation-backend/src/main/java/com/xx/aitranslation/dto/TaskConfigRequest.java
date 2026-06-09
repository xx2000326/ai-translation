package com.xx.aitranslation.dto;

import lombok.Data;

/**
 * 任务配置请求体：保存翻译要求、语言、模型与开关等配置。
 * <p>
 * 字段均为可选，服务层仅对非 null 字段进行覆盖。
 */
@Data
public class TaskConfigRequest {

    /** 翻译要求 */
    private String requirement;

    /** 任务描述 */
    private String description;

    /** 源语言 code */
    private String sourceLang;

    /** 目标语言 code */
    private String targetLang;

    /**
     * 解析拆分粒度（已废弃，保留兼容）。
     * @deprecated 改用 {@link #chunkStrategy} 等拆分配置。
     */
    @Deprecated
    private String parseGranularity;

    /** 文档拆分策略：ChunkStrategyType 名称，或 AUTO（按文件类型自动选择） */
    private String chunkStrategy;

    /** 固定长度策略：单块字符数 */
    private Integer chunkSize;

    /** 相邻块重叠字符数 */
    private Integer overlap;

    /** 层级策略：父块字符数 */
    private Integer parentSize;

    /** 层级策略：子块字符数 */
    private Integer childSize;

    /** Markdown：最大拆分标题深度 1~6，写入 extra_data.chunk.headingLevel */
    private Integer chunkHeadingLevel;

    /** 是否启用术语库 */
    private Boolean enableGlossary;

    /** 是否启用历史 / RAG 记忆 */
    private Boolean enableHistory;

    /** 翻译使用的模型 code */
    private String translateModel;

    /** 是否启用 AI 审校 */
    private Boolean enableReview;

    /** 审校使用的模型 code */
    private String reviewModel;

    /** 是否启用全文风格统一（汇总 Agent） */
    private Boolean enableSummary;
}
