package com.xx.aitranslation.dto;

import com.xx.aitranslation.service.chunk.model.ChunkResult;
import com.xx.aitranslation.service.chunk.model.DocumentChunk;
import lombok.Builder;
import lombok.Data;
import org.springframework.util.ObjectUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 文档拆分预览响应（供前端展示拆分效果，不落库）。
 */
@Data
@Builder
public class ChunkPreviewResponse {

    private String fileName;

    private String fileType;

    private String strategy;

    private int chunkCount;

    private int totalChars;

    private int totalTokens;

    private List<ChunkView> chunks;

    /**
     * 单个块视图。
     */
    @Data
    @Builder
    public static class ChunkView {
        private String id;
        private String parentId;
        private Integer level;
        private Integer index;
        private String title;
        private Integer charCount;
        private Integer tokenCount;
        private String content;
    }

    /**
     * 由引擎结果转换为前端视图。
     */
    public static ChunkPreviewResponse from(ChunkResult result) {
        List<ChunkView> views = new ArrayList<>();
        int totalChars = 0;
        int totalTokens = 0;
        if (!ObjectUtils.isEmpty(result.getChunks())) {
            for (DocumentChunk chunk : result.getChunks()) {
                int chars = chunk.getCharCount() == null ? 0 : chunk.getCharCount();
                int tokens = chunk.getTokenCount() == null ? 0 : chunk.getTokenCount();
                // Root 节点（level=0）仅用于层级展示，不计入字符/token 合计，避免与子块重复
                if (chunk.getLevel() != null && chunk.getLevel() > 0) {
                    totalChars += chars;
                    totalTokens += tokens;
                }
                views.add(ChunkView.builder()
                        .id(chunk.getId())
                        .parentId(chunk.getParentId())
                        .level(chunk.getLevel())
                        .index(chunk.getIndex())
                        .title(extractTitle(chunk))
                        .charCount(chunk.getCharCount())
                        .tokenCount(chunk.getTokenCount())
                        .content(chunk.getContent())
                        .build());
            }
        }
        return ChunkPreviewResponse.builder()
                .fileName(result.getFileName())
                .fileType(result.getFileType() == null ? null : result.getFileType().name())
                .strategy(result.getStrategy() == null ? null : result.getStrategy().name())
                .chunkCount(views.size())
                .totalChars(totalChars)
                .totalTokens(totalTokens)
                .chunks(views)
                .build();
    }

    private static String extractTitle(DocumentChunk chunk) {
        Map<String, Object> metadata = chunk.getMetadata();
        if (ObjectUtils.isEmpty(metadata)) {
            return null;
        }
        Object title = metadata.get("title");
        return title == null ? null : title.toString();
    }
}
