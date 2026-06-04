package com.xx.aitranslation.enums;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * 支持的源 / 目标语言。
 */
public enum Language {

    ZH("zh", "中文"),
    EN("en", "英文");

    /**
     * 语言选项视图，供前端语言下拉使用（含 code 与展示 label）。
     */
    public record LanguageOption(String code, String label) {
    }

    private final String code;
    private final String label;

    Language(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 返回所有语言选项（含 code + label）。
     */
    public static List<LanguageOption> codes() {
        return Arrays.stream(values())
                .map(l -> new LanguageOption(l.code, l.label))
                .toList();
    }

    /**
     * 根据 code 取展示 label，未匹配返回原 code。
     */
    public static String labelOf(String code) {
        if (Objects.isNull(code)) {
            return null;
        }
        return Arrays.stream(values())
                .filter(l -> l.code.equalsIgnoreCase(code))
                .map(Language::getLabel)
                .findFirst()
                .orElse(code);
    }
}
