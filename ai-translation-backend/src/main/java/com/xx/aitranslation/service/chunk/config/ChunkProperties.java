package com.xx.aitranslation.service.chunk.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

/**
 * 文档拆分引擎全局配置，绑定 {@code document.chunk.*}。
 * <p>
 * 提供默认参数，并可通过 {@link #toConfig()} 转换为单次调用的 {@link ChunkConfig} 快照。
 */
@Data
@Component
@ConfigurationProperties(prefix = "document.chunk")
public class ChunkProperties {

    /** 是否启用拆分引擎。 */
    private boolean enabled = true;

    /** 默认拆分策略（为空表示由工厂按文件类型自动选择）。 */
    private ChunkStrategyType strategy;

    /** 固定长度策略：单块字符数。 */
    private int chunkSize = 1000;

    /** Overlap：相邻块重叠字符数。 */
    private int overlap = 100;

    /** 层级策略：父块字符数。 */
    private int parentSize = 5000;

    /** 层级策略：子块字符数。 */
    private int childSize = 1000;

    /** 触发层级拆分的内容长度阈值（字符数）。 */
    private int hierarchicalThreshold = 50000;

    /**
     * 基于全局默认值构建单次调用配置快照。
     */
    public ChunkConfig toConfig() {
        return ChunkConfig.builder()
                .strategy(strategy)
                .chunkSize(chunkSize)
                .overlap(overlap)
                .parentSize(parentSize)
                .childSize(childSize)
                .hierarchicalThreshold(hierarchicalThreshold)
                .build();
    }

    /**
     * 合并外部传入配置：外部为 {@code null} 时回退到全局默认值。
     */
    public ChunkConfig resolve(ChunkConfig override) {
        return ObjectUtils.isEmpty(override) ? toConfig() : override;
    }
}
