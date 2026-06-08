package com.xx.aitranslation.service.chunk;

import com.xx.aitranslation.entity.DocumentChunkEntity;
import com.xx.aitranslation.mapper.DocumentChunkMapper;
import com.xx.aitranslation.service.chunk.model.ChunkResult;
import com.xx.aitranslation.service.chunk.model.DocumentChunk;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 拆分结果持久化服务：将 {@link ChunkResult} 落库至 {@code document_chunk}。
 * <p>
 * 与拆分引擎 {@link ChunkEngine} 解耦——引擎只负责产出结果，是否落库由调用方决定。
 */
@Service
public class ChunkPersistService {

    private final DocumentChunkMapper documentChunkMapper;

    public ChunkPersistService(DocumentChunkMapper documentChunkMapper) {
        this.documentChunkMapper = documentChunkMapper;
    }

    /**
     * 保存拆分结果。
     *
     * @param result   拆分结果
     * @param sourceId 来源标识（业务可空）
     * @return 保存的块数量
     */
    @Transactional(rollbackFor = Exception.class)
    public int save(ChunkResult result, Long sourceId) {
        if (ObjectUtils.isEmpty(result) || ObjectUtils.isEmpty(result.getChunks())) {
            return 0;
        }
        List<DocumentChunkEntity> entities = new ArrayList<>(result.getChunks().size());
        for (DocumentChunk chunk : result.getChunks()) {
            entities.add(toEntity(result, chunk, sourceId));
        }
        for (DocumentChunkEntity entity : entities) {
            documentChunkMapper.insert(entity);
        }
        return entities.size();
    }

    private DocumentChunkEntity toEntity(ChunkResult result, DocumentChunk chunk, Long sourceId) {
        DocumentChunkEntity entity = new DocumentChunkEntity();
        entity.setSourceId(sourceId);
        entity.setSourceName(result.getFileName());
        entity.setFileType(result.getFileType() == null ? null : result.getFileType().name());
        entity.setStrategy(result.getStrategy() == null ? null : result.getStrategy().name());
        entity.setChunkKey(chunk.getId());
        entity.setParentKey(chunk.getParentId());
        entity.setLevel(chunk.getLevel());
        entity.setOrderNo(chunk.getIndex());
        entity.setContent(chunk.getContent());
        entity.setTokenCount(chunk.getTokenCount());
        entity.setCharCount(chunk.getCharCount());
        entity.setTitle(extractTitle(chunk));
        return entity;
    }

    private String extractTitle(DocumentChunk chunk) {
        Map<String, Object> metadata = chunk.getMetadata();
        if (ObjectUtils.isEmpty(metadata)) {
            return null;
        }
        Object title = metadata.get("title");
        return title == null ? null : title.toString();
    }
}
