package com.xx.aitranslation.service.chunk.support;

import com.xx.aitranslation.service.chunk.model.DocumentChunk;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.Map;

/**
 * 块构建辅助：统一生成块 ID、补全字符数 / token 数等元信息，供各拆分策略复用。
 */
@Component
public class ChunkFactorySupport {

    private final TokenCounter tokenCounter;

    public ChunkFactorySupport(TokenCounter tokenCounter) {
        this.tokenCounter = tokenCounter;
    }

    /**
     * 生成文档内唯一块 ID，形如 {@code L1-0}（层级-序号）。
     */
    public String buildId(int level, int index) {
        return "L" + level + "-" + index;
    }

    /**
     * 构建一个块并自动补全字符数 / token 数。
     *
     * @param content  块内容
     * @param parentId 父块 ID（根级传 {@code null}）
     * @param level    层级
     * @param index    同层序号
     * @param metadata 元数据（可为 {@code null}）
     */
    public DocumentChunk build(String content, String parentId, int level, int index, Map<String, Object> metadata) {
        String text = content == null ? "" : content;
        DocumentChunk.DocumentChunkBuilder builder = DocumentChunk.builder()
                .id(buildId(level, index))
                .parentId(parentId)
                .level(level)
                .index(index)
                .content(text)
                .charCount(text.length())
                .tokenCount(tokenCounter.estimate(text));
        if (!ObjectUtils.isEmpty(metadata)) {
            builder.metadata(metadata);
        }
        return builder.build();
    }

    /**
     * 构建一个扁平块（level = 1，无父块）。
     */
    public DocumentChunk buildFlat(String content, int index) {
        return build(content, null, 1, index, null);
    }

    /**
     * 使用显式块 ID 构建块（用于标题树等需要自定义 ID/父子引用的场景），并自动补全字符数 / token 数。
     */
    public DocumentChunk buildWithId(String id, String content, String parentId, int level, int index,
                                     Map<String, Object> metadata) {
        String text = content == null ? "" : content;
        DocumentChunk.DocumentChunkBuilder builder = DocumentChunk.builder()
                .id(id)
                .parentId(parentId)
                .level(level)
                .index(index)
                .content(text)
                .charCount(text.length())
                .tokenCount(tokenCounter.estimate(text));
        if (!ObjectUtils.isEmpty(metadata)) {
            builder.metadata(metadata);
        }
        return builder.build();
    }
}
