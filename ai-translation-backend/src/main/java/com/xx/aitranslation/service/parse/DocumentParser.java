package com.xx.aitranslation.service.parse;

import com.xx.aitranslation.enums.FileType;
import com.xx.aitranslation.enums.ParseGranularity;

import java.io.InputStream;

/**
 * 文档解析器：将源文件输入流解析为文件/段落/句子结构。
 */
public interface DocumentParser {

    /**
     * 解析源文件。
     *
     * @param in          源文件输入流
     * @param sourceLang  源语言 code（影响 SRX 分句规则）
     * @param granularity 拆分粒度：按句 / 按段
     * @return 解析结果（文件 → 段落 → 句子）
     */
    ParsedDocument parse(InputStream in, String sourceLang, ParseGranularity granularity) throws Exception;

    FileType supportType();
}
