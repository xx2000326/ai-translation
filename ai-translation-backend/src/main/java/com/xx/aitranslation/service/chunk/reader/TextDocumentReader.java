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
 * 纯文本读取器：以 UTF-8 直接读取原始文本，保留换行结构。
 */
@Slf4j
@Component
public class TextDocumentReader implements DocumentReader {

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
            log.error("文本文件读取失败: {}", fileName, e);
            throw new BizException("chunk.read.failed");
        }
    }

    @Override
    public ChunkFileType supportType() {
        return ChunkFileType.TEXT;
    }
}
