package com.xx.aitranslation.service.chunk.reader;

import com.xx.aitranslation.common.BizException;
import com.xx.aitranslation.service.chunk.config.ChunkFileType;
import com.xx.aitranslation.service.chunk.model.ParsedDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * Word（DOC/DOCX）读取器：基于 Spring AI {@link TikaDocumentReader}（底层 Apache Tika）抽取文本。
 */
@Slf4j
@Component
public class WordDocumentReader extends AbstractSpringAiDocumentReader {

    @Override
    public ParsedDocument read(Resource resource, String fileName) {
        try {
            TikaDocumentReader reader = new TikaDocumentReader(resource);
            String content = joinText(reader.read());
            return ParsedDocument.builder()
                    .fileName(fileName)
                    .fileType(supportType())
                    .content(content)
                    .build();
        } catch (Exception e) {
            log.error("Word 文件读取失败: {}", fileName, e);
            throw new BizException("chunk.read.failed");
        }
    }

    @Override
    public ChunkFileType supportType() {
        return ChunkFileType.WORD;
    }
}
