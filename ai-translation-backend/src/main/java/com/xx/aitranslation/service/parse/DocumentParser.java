package com.xx.aitranslation.service.parse;

import com.xx.aitranslation.enums.FileType;

import java.io.InputStream;
import java.util.List;

/**
 * 文档解析器：将源文件输入流解析为文件/段落/句子结构。
 */
public interface DocumentParser {

    ParsedDocument parse(InputStream in, String sourceLang) throws Exception;

    FileType supportType();
}
