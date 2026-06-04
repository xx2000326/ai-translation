package com.xx.aitranslation.enums;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * 翻译风格：决定译文的语气与表达方式。
 */
public enum TranslationStyle {

    /** 正式 */
    FORMAL("formal", "正式、规范、书面化"),
    /** 简洁 */
    CONCISE("concise", "简洁、凝练、直击要点"),
    /** 创意 */
    CREATIVE("creative", "富有创意、生动、有想象力"),
    /** 情绪化 */
    EMOTIONAL("emotional", "富有情绪感染力、打动人心");

    private final String code;
    private final String description;

    TranslationStyle(String code, String description) {
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
        return Arrays.stream(values()).map(TranslationStyle::getCode).toList();
    }

    public static TranslationStyle fromCode(String code) {
        if (Objects.isNull(code)) {
            return null;
        }
        return Arrays.stream(values())
                .filter(s -> s.code.equalsIgnoreCase(code))
                .findFirst()
                .orElse(null);
    }
}
