package com.xx.aitranslation.service.chunk;

import com.xx.aitranslation.common.BizException;
import com.xx.aitranslation.service.chunk.cleaner.DocumentCleanerChain;
import com.xx.aitranslation.service.chunk.config.ChunkConfig;
import com.xx.aitranslation.service.chunk.config.ChunkFileType;
import com.xx.aitranslation.service.chunk.config.ChunkProperties;
import com.xx.aitranslation.service.chunk.config.ChunkStrategyType;
import com.xx.aitranslation.service.chunk.model.ChunkResult;
import com.xx.aitranslation.service.chunk.model.ChunkTree;
import com.xx.aitranslation.service.chunk.model.DocumentChunk;
import com.xx.aitranslation.service.chunk.model.ParsedDocument;
import com.xx.aitranslation.service.chunk.reader.DocumentReaderFactory;
import com.xx.aitranslation.service.chunk.strategy.ChunkStrategyFactory;
import com.xx.aitranslation.service.chunk.strategy.HierarchicalChunkStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * 文档拆分引擎入口：编排 读取 → 清洗 → 拆分 全流程。
 * <p>
 * 该引擎为独立通用模块，不依赖具体业务（翻译 / RAG / 摘要），可被上层直接复用。
 */
@Slf4j
@Service
public class ChunkEngine {

    private final DocumentReaderFactory readerFactory;
    private final DocumentCleanerChain cleanerChain;
    private final ChunkStrategyFactory strategyFactory;
    private final ChunkProperties chunkProperties;

    public ChunkEngine(DocumentReaderFactory readerFactory,
                       DocumentCleanerChain cleanerChain,
                       ChunkStrategyFactory strategyFactory,
                       ChunkProperties chunkProperties) {
        this.readerFactory = readerFactory;
        this.cleanerChain = cleanerChain;
        this.strategyFactory = strategyFactory;
        this.chunkProperties = chunkProperties;
    }

    /**
     * 使用全局默认配置拆分输入流。
     */
    public ChunkResult chunk(InputStream in, String fileName) {
        return chunk(in, fileName, null);
    }

    /**
     * 拆分输入流。
     *
     * @param in       源文件输入流
     * @param fileName 原始文件名（用于识别类型与策略）
     * @param config   拆分配置；为 {@code null} 时使用全局默认值
     */
    public ChunkResult chunk(InputStream in, String fileName, ChunkConfig config) {
        if (ObjectUtils.isEmpty(in)) {
            throw new BizException("chunk.content.empty");
        }
        try {
            // 读入字节后用 ByteArrayResource 包装，避免二进制读取器（PDF/Word）对流的多次读取/长度探测失败
            byte[] bytes = in.readAllBytes();
            return chunk(new ByteArrayResource(bytes), fileName, config);
        } catch (IOException e) {
            log.error("拆分输入流读取失败: {}", fileName, e);
            throw new BizException("chunk.read.failed");
        }
    }

    /**
     * 拆分文件资源。
     *
     * @param resource 源文件资源
     * @param fileName 原始文件名
     * @param config   拆分配置；为 {@code null} 时使用全局默认值
     */
    public ChunkResult chunk(Resource resource, String fileName, ChunkConfig config) {
        ChunkConfig effectiveConfig = chunkProperties.resolve(config);
        ChunkFileType fileType = ChunkFileType.fromFileName(fileName);

        // 1. 读取
        ParsedDocument parsed = readerFactory.get(fileType).read(resource, fileName);
        // 2. 清洗
        String cleaned = cleanerChain.clean(parsed.getContent());
        if (ObjectUtils.isEmpty(cleaned)) {
            throw new BizException("chunk.content.empty");
        }
        // 3. 选择策略并拆分
        ChunkStrategyType strategyType = strategyFactory.resolveType(fileType, cleaned, effectiveConfig);
        log.debug("文档拆分: file={}, type={}, strategy={}, length={}", fileName, fileType, strategyType, cleaned.length());

        if (strategyType == ChunkStrategyType.HIERARCHICAL) {
            return hierarchicalResult(fileName, fileType, cleaned, effectiveConfig);
        }
        return flatResult(fileName, fileType, strategyType, cleaned, effectiveConfig);
    }

    private ChunkResult flatResult(String fileName, ChunkFileType fileType, ChunkStrategyType strategyType,
                                   String content, ChunkConfig config) {
        List<DocumentChunk> chunks = strategyFactory.getFlatStrategy(strategyType).chunk(content, config);
        return ChunkResult.builder()
                .fileName(fileName)
                .fileType(fileType)
                .strategy(strategyType)
                .chunks(chunks)
                .build();
    }

    private ChunkResult hierarchicalResult(String fileName, ChunkFileType fileType,
                                           String content, ChunkConfig config) {
        HierarchicalChunkStrategy strategy = strategyFactory.getHierarchicalStrategy();
        ChunkTree tree = strategy.chunk(content, config);
        return ChunkResult.builder()
                .fileName(fileName)
                .fileType(fileType)
                .strategy(ChunkStrategyType.HIERARCHICAL)
                .chunks(tree.flatten())
                .tree(tree)
                .build();
    }
}
