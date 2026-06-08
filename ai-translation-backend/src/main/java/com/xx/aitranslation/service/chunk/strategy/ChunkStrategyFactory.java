package com.xx.aitranslation.service.chunk.strategy;

import com.xx.aitranslation.common.BizException;
import com.xx.aitranslation.service.chunk.config.ChunkConfig;
import com.xx.aitranslation.service.chunk.config.ChunkFileType;
import com.xx.aitranslation.service.chunk.config.ChunkStrategyType;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 拆分策略工厂：按"显式配置 &gt; 内容规模 &gt; 文件类型默认"的优先级选择策略。
 * <p>
 * 文件类型默认映射：
 * <ul>
 *     <li>Markdown → {@link ChunkStrategyType#MARKDOWN}</li>
 *     <li>PDF / Word → {@link ChunkStrategyType#TITLE}（按标题结构层级拆分）</li>
 *     <li>HTML → {@link ChunkStrategyType#PARAGRAPH}</li>
 *     <li>Text → {@link ChunkStrategyType#SENTENCE}</li>
 *     <li>内容超过阈值 → {@link ChunkStrategyType#HIERARCHICAL}</li>
 * </ul>
 */
@Component
public class ChunkStrategyFactory {

    private final Map<ChunkStrategyType, ChunkStrategy> flatStrategies = new EnumMap<>(ChunkStrategyType.class);
    private final HierarchicalChunkStrategy hierarchicalChunkStrategy;

    public ChunkStrategyFactory(List<ChunkStrategy> strategies, HierarchicalChunkStrategy hierarchicalChunkStrategy) {
        for (ChunkStrategy strategy : strategies) {
            flatStrategies.put(strategy.type(), strategy);
        }
        this.hierarchicalChunkStrategy = hierarchicalChunkStrategy;
    }

    /**
     * 解析最终采用的策略类型。
     *
     * @param fileType 文件类型
     * @param content  清洗后的文本（用于规模判断）
     * @param config   拆分配置
     */
    public ChunkStrategyType resolveType(ChunkFileType fileType, String content, ChunkConfig config) {
        if (!ObjectUtils.isEmpty(config.getStrategy())) {
            return config.getStrategy();
        }
        int length = content == null ? 0 : content.length();
        if (length > config.getHierarchicalThreshold()) {
            return ChunkStrategyType.HIERARCHICAL;
        }
        return defaultByFileType(fileType);
    }

    /**
     * 获取扁平策略实现，类型不支持（如层级）时抛出业务异常。
     */
    public ChunkStrategy getFlatStrategy(ChunkStrategyType type) {
        ChunkStrategy strategy = flatStrategies.get(type);
        if (ObjectUtils.isEmpty(strategy)) {
            throw new BizException("chunk.strategy.unsupported");
        }
        return strategy;
    }

    /**
     * 获取层级策略实现。
     */
    public HierarchicalChunkStrategy getHierarchicalStrategy() {
        return hierarchicalChunkStrategy;
    }

    private ChunkStrategyType defaultByFileType(ChunkFileType fileType) {
        return switch (fileType) {
            case MARKDOWN -> ChunkStrategyType.MARKDOWN;
            case PDF, WORD -> ChunkStrategyType.TITLE;
            case HTML -> ChunkStrategyType.PARAGRAPH;
            case TEXT -> ChunkStrategyType.SENTENCE;
        };
    }
}
