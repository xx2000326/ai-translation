package com.xx.aitranslation.service.chunk.reader;

import com.xx.aitranslation.common.BizException;
import com.xx.aitranslation.service.chunk.config.ChunkFileType;
import com.xx.aitranslation.service.chunk.model.ParsedDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Markdown 读取器。
 * <p>
 * 注意：此处刻意以原始文本读取，保留 {@code #} 标题等 Markdown 结构，
 * 以便后续 {@code MarkdownChunkStrategy} 按标题层级拆分。
 * Spring AI 的 {@code spring-ai-markdown-document-reader} 会丢弃标题标记，故不在此使用，保留依赖供未来结构化场景。
 */
@Slf4j
@Component
public class MarkdownDocumentReader implements DocumentReader {

    @Override
    public ParsedDocument read(Resource resource, String fileName) {
        try (InputStream in = resource.getInputStream()) {
            String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return ParsedDocument.builder()
                    .fileName(fileName)
                    .fileType(supportType())
                    .content(content)
                    .build();
        } catch (IOException e) {
            log.error("Markdown 文件读取失败: {}", fileName, e);
            throw new BizException("chunk.read.failed");
        }
    }

    @Override
    public ChunkFileType supportType() {
        return ChunkFileType.MARKDOWN;
    }
}
