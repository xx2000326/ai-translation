package com.xx.aitranslation.service.chunk.reader;

import com.xx.aitranslation.common.BizException;
import com.xx.aitranslation.service.chunk.config.ChunkFileType;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 文档读取器工厂：按 {@link ChunkFileType} 路由到具体读取器。
 */
@Component
public class DocumentReaderFactory {

    private final Map<ChunkFileType, DocumentReader> readers = new EnumMap<>(ChunkFileType.class);

    public DocumentReaderFactory(List<DocumentReader> readerList) {
        for (DocumentReader reader : readerList) {
            readers.put(reader.supportType(), reader);
        }
    }

    /**
     * 获取指定文件类型的读取器，不支持时抛出业务异常。
     */
    public DocumentReader get(ChunkFileType fileType) {
        DocumentReader reader = readers.get(fileType);
        if (ObjectUtils.isEmpty(reader)) {
            throw new BizException("chunk.file.type.unsupported");
        }
        return reader;
    }
}
