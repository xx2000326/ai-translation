package com.xx.aitranslation.service.parse;

import org.springframework.util.ObjectUtils;

import java.util.regex.Pattern;

/**
 * 文本清洗与不可译判定（参考 yunshu TranslationUtils / PlainTextHelper）。
 */
public final class ParseTextUtils {

    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\r\\n\\t]+");
    private static final Pattern SYMBOL_ONLY = Pattern.compile("[\\p{P}\\p{S}\\s]+");
    private static final Pattern EMAIL = Pattern.compile("^[\\w.+-]+@[\\w.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern URL = Pattern.compile("^(https?|ftp)://\\S+$", Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMBER = Pattern.compile("^[-+]?\\d+(\\.\\d+)?$");

    private ParseTextUtils() {
    }

    public static String removeControlChar(String text) {
        if (ObjectUtils.isEmpty(text)) {
            return text;
        }
        String normalized = text.replace('\u00a0', ' ');
        return CONTROL_CHARS.matcher(normalized).replaceAll(" ").trim();
    }

    public static boolean isNonTranslationText(String text) {
        if (ObjectUtils.isEmpty(text)) {
            return true;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return true;
        }
        if (EMAIL.matcher(trimmed).matches()) {
            return true;
        }
        if (URL.matcher(trimmed).matches()) {
            return true;
        }
        if (NUMBER.matcher(trimmed).matches()) {
            return true;
        }
        return SYMBOL_ONLY.matcher(trimmed).matches();
    }
}
