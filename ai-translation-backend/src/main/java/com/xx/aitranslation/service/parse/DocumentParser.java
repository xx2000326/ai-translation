package com.xx.aitranslation.service.parse;

import com.xx.aitranslation.enums.FileType;

import java.io.InputStream;
import java.util.List;

/**
 * 文档解析器：将源文件输入流解析为有序的内容块列表。
 * <p>
 * 每种 {@link FileType} 对应一个实现，由 {@link DocumentParserFactory} 按类型路由。
 */
public interface DocumentParser {

    /**
     * 解析输入流为内容块列表。
     *
     * @param in 源文件输入流
     * @return 按文档顺序排列的内容块
     * @throws Exception 解析过程中的任意异常，由编排器统一兜底
     */
    List<ParsedBlock> parse(InputStream in) throws Exception;

    /**
     * 该解析器支持的文件类型。
     */
    FileType supportType();
}
