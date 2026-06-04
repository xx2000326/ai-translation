package com.xx.aitranslation.enums;

import com.xx.aitranslation.common.BizException;
import org.springframework.util.ObjectUtils;

import java.util.Arrays;

/**
 * 支持解析的源文件类型，按文件后缀识别。
 */
public enum FileType {

    TXT,
    DOCX,
    HTML;

    /**
     * 按文件名后缀推断文件类型，无法识别时抛出业务异常。
     *
     * @param name 原始文件名（含扩展名）
     * @return 匹配的 {@link FileType}
     */
    public static FileType fromFileName(String name) {
        if (ObjectUtils.isEmpty(name)) {
            throw new BizException("file.type.unsupported");
        }
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            throw new BizException("file.type.unsupported");
        }
        String ext = name.substring(dot + 1);
        return Arrays.stream(values())
                .filter(t -> t.name().equalsIgnoreCase(ext))
                .findFirst()
                .orElseThrow(() -> new BizException("file.type.unsupported"));
    }
}
