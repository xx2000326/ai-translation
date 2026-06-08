package com.xx.aitranslation.service.chunk.config;

import lombok.Builder;
import lombok.Data;

/**
 * 单次拆分请求的运行时配置（值对象）。
 * <p>
 * 与 {@link ChunkProperties} 区分：{@link ChunkProperties} 提供全局默认值，
 * {@link ChunkConfig} 为每次调用可覆盖的快照，便于不同任务使用不同参数，保证拆分逻辑无状态、可复用。
 */
@Data
@Builder
public class ChunkConfig {

    /**
     * 指定拆分策略；为 {@code null} 时由工厂按文件类型自动选择。
     */
    private ChunkStrategyType strategy;

    /** 固定长度策略：单块字符数。 */
    @Builder.Default
    private int chunkSize = 1000;

    /** Overlap：相邻块重叠字符数，避免上下文断裂。 */
    @Builder.Default
    private int overlap = 100;

    /** 层级策略：父块字符数。 */
    @Builder.Default
    private int parentSize = 5000;

    /** 层级策略：子块字符数。 */
    @Builder.Default
    private int childSize = 1000;

    /**
     * 触发层级拆分的内容长度阈值（字符数）。
     * 当未显式指定策略且内容超过该阈值时，自动切换为层级拆分。
     */
    @Builder.Default
    private int hierarchicalThreshold = 50000;
}
