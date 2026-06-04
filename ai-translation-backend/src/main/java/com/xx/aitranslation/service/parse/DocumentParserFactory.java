package com.xx.aitranslation.service.parse;

import com.xx.aitranslation.common.BizException;
import com.xx.aitranslation.enums.FileType;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 文档解析器工厂：按 {@link FileType} 路由到具体解析器。
 */
@Component
public class DocumentParserFactory {

    private final Map<FileType, DocumentParser> parsers = new EnumMap<>(FileType.class);

    public DocumentParserFactory(List<DocumentParser> parserList) {
        for (DocumentParser parser : parserList) {
            parsers.put(parser.supportType(), parser);
        }
    }

    /**
     * 获取指定文件类型的解析器，不支持时抛出业务异常。
     */
    public DocumentParser get(FileType fileType) {
        DocumentParser parser = parsers.get(fileType);
        if (ObjectUtils.isEmpty(parser)) {
            throw new BizException("file.type.unsupported");
        }
        return parser;
    }
}
