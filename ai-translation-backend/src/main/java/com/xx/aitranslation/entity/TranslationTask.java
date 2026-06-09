package com.xx.aitranslation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xx.aitranslation.dto.TaskStepDto;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 翻译任务实体，对应 MySQL translation_task 表。
 * <p>
 * 一个任务对应一次文件翻译流程，承载任务配置（模型 / 语言 / 是否审校等）、源文件信息与状态机字段。
 */
@Data
@TableName("translation_task")
public class TranslationTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联项目ID */
    private Long projectId;

    /** 关联客户ID */
    private Long customerId;

    /** 任务状态，存 {@link com.xx.aitranslation.enums.TaskStatus} 的 name() */
    private String status;

    /** 翻译要求（用户填写的整体要求） */
    private String requirement;

    /** 任务描述 */
    private String description;

    /** 源语言 code */
    private String sourceLang;

    /** 目标语言 code */
    private String targetLang;

    /**
     * 解析拆分粒度（已废弃，保留兼容历史数据）。
     * @deprecated 工作流解析已改用文档拆分引擎，改由 {@link #chunkStrategy} 等字段控制。
     */
    @Deprecated
    private String parseGranularity;

    /** 文档拆分策略，存 {@link com.xx.aitranslation.service.chunk.config.ChunkStrategyType} 的 name()，AUTO 表示自动选择 */
    private String chunkStrategy;

    /** 固定长度策略：单块字符数 */
    private Integer chunkSize;

    /** 相邻块重叠字符数 */
    private Integer chunkOverlap;

    /** 层级策略：父块字符数 */
    private Integer chunkParentSize;

    /** 层级策略：子块字符数 */
    private Integer chunkChildSize;

    /** 扩展配置 JSON（chunk.headingLevel 等） */
    private String extraData;

    /** Markdown 标题拆分深度（非表字段，详情接口填充） */
    @TableField(exist = false)
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

    /** 是否启用全文风格统一（汇总 Agent，V1 模块七） */
    private Boolean enableSummary;

    /** 审校综合评分 */
    private Integer reviewScore;

    /** 审校轮次 */
    private Integer reviewRound;

    /** 源文件原始名称 */
    private String sourceFileName;

    /** 源文件存储 key（对象存储 / 本地） */
    private String sourceFileKey;

    /** 源文件类型，存 {@link com.xx.aitranslation.enums.FileType} 的 name() */
    private String sourceFileType;

    /** 失败原因 */
    private String errorMsg;

    /** 任务步骤进度（非表字段，详情接口填充） */
    @TableField(exist = false)
    private List<TaskStepDto> steps;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
