package com.xx.aitranslation.service.chunk.config;

import com.xx.aitranslation.common.BizException;
import org.springframework.util.ObjectUtils;

import java.util.Arrays;
import java.util.Locale;

/**
 * 文档拆分引擎支持的文件格式，按文件后缀识别。
 * <p>
 * 该枚举独立于业务解析模块（{@code com.xx.aitranslation.enums.FileType}），
 * 以保证拆分引擎可作为通用模块独立复用。
 */
public enum ChunkFileType {

    PDF("pdf"),
    WORD("doc", "docx"),
    HTML("html", "htm"),
    MARKDOWN("md", "markdown"),
    TEXT("txt", "text");

    private final String[] extensions;

    ChunkFileType(String... extensions) {
        this.extensions = extensions;
    }

    /**
     * 按文件名后缀推断文件类型，无法识别时抛出业务异常。
     *
     * @param fileName 原始文件名（含扩展名）
     * @return 匹配的 {@link ChunkFileType}
     */
    public static ChunkFileType fromFileName(String fileName) {
        if (ObjectUtils.isEmpty(fileName)) {
            throw new BizException("chunk.file.type.unsupported");
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            throw new BizException("chunk.file.type.unsupported");
        }
        String ext = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(type -> Arrays.asList(type.extensions).contains(ext))
                .findFirst()
                .orElseThrow(() -> new BizException("chunk.file.type.unsupported"));
    }
}
