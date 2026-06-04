package com.xx.aitranslation.enums;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * 翻译角色：决定翻译时的专业领域侧重。
 */
public enum TranslationRole {

    /** 专业文档 */
    PROFESSIONAL("professional", "专业文档翻译专家，注重术语准确、逻辑严谨、忠实原意"),
    /** 文学 */
    LITERATURE("literature", "文学翻译家，注重文采、意境与韵律，兼顾信达雅"),
    /** 广告 */
    ADVERTISEMENT("advertisement", "广告文案翻译专家，注重吸引力、传播性与本地化表达");

    private final String code;
    private final String description;

    TranslationRole(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static List<String> codes() {
        return Arrays.stream(values()).map(TranslationRole::getCode).toList();
    }

    public static TranslationRole fromCode(String code) {
        if (Objects.isNull(code)) {
            return null;
        }
        return Arrays.stream(values())
                .filter(r -> r.code.equalsIgnoreCase(code))
                .findFirst()
                .orElse(null);
    }
}
