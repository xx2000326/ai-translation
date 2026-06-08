package com.xx.aitranslation.service.chunk.reader;

import com.xx.aitranslation.common.BizException;
import com.xx.aitranslation.service.chunk.config.ChunkFileType;
import com.xx.aitranslation.service.chunk.model.ParsedDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.reader.jsoup.JsoupDocumentReader;
import org.springframework.ai.reader.jsoup.config.JsoupDocumentReaderConfig;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * HTML 读取器：基于 Spring AI {@link JsoupDocumentReader}（底层 Jsoup）抽取正文文本。
 */
@Slf4j
@Component
public class HtmlDocumentReader extends AbstractSpringAiDocumentReader {

    @Override
    public ParsedDocument read(Resource resource, String fileName) {
        try {
            JsoupDocumentReaderConfig config = JsoupDocumentReaderConfig.builder()
                    .selector("body")
                    .build();
            JsoupDocumentReader reader = new JsoupDocumentReader(resource, config);
            String content = joinText(reader.read());
            return ParsedDocument.builder()
                    .fileName(fileName)
                    .fileType(supportType())
                    .content(content)
                    .build();
        } catch (Exception e) {
            log.error("HTML 文件读取失败: {}", fileName, e);
            throw new BizException("chunk.read.failed");
        }
    }

    @Override
    public ChunkFileType supportType() {
        return ChunkFileType.HTML;
    }
}
