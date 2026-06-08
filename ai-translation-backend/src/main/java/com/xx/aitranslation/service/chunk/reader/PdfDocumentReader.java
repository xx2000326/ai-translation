package com.xx.aitranslation.service.chunk.reader;

import com.xx.aitranslation.common.BizException;
import com.xx.aitranslation.service.chunk.config.ChunkFileType;
import com.xx.aitranslation.service.chunk.model.ParsedDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * PDF 读取器：基于 Spring AI {@link PagePdfDocumentReader}（底层 PDFBox）按页抽取文本。
 */
@Slf4j
@Component
public class PdfDocumentReader extends AbstractSpringAiDocumentReader {

    @Override
    public ParsedDocument read(Resource resource, String fileName) {
        try {
            PdfDocumentReaderConfig config = PdfDocumentReaderConfig.builder()
                    .withPagesPerDocument(1)
                    .build();
            PagePdfDocumentReader reader = new PagePdfDocumentReader(resource, config);
            String content = joinText(reader.read());
            return ParsedDocument.builder()
                    .fileName(fileName)
                    .fileType(supportType())
                    .content(content)
                    .build();
        } catch (Exception e) {
            log.error("PDF 文件读取失败: {}", fileName, e);
            throw new BizException("chunk.read.failed");
        }
    }

    @Override
    public ChunkFileType supportType() {
        return ChunkFileType.PDF;
    }
}
