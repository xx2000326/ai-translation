package com.xx.aitranslation.service.chunk.cleaner;

import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.regex.Pattern;

/**
 * 页码清洗：移除独占一行的页码（如 {@code 12}、{@code - 12 -}、{@code 第 12 页}、{@code Page 12 of 30}）。
 * <p>
 * 仅清除"整行仅为页码"的情形，避免误伤正文中的数字。
 */
@Component
public class PageNumberCleaner implements DocumentCleaner {

    private static final Pattern PURE_NUMBER_LINE = Pattern.compile("(?m)^\\s*[-—–]?\\s*\\d{1,4}\\s*[-—–]?\\s*$");

    private static final Pattern CN_PAGE_LINE = Pattern.compile("(?m)^\\s*第\\s*\\d{1,4}\\s*页(\\s*/?\\s*共?\\s*\\d{1,4}\\s*页)?\\s*$");

    private static final Pattern EN_PAGE_LINE = Pattern.compile("(?mi)^\\s*page\\s*\\d{1,4}(\\s*(of|/)\\s*\\d{1,4})?\\s*$");

    @Override
    public String clean(String content) {
        if (ObjectUtils.isEmpty(content)) {
            return content;
        }
        String result = PURE_NUMBER_LINE.matcher(content).replaceAll("");
        result = CN_PAGE_LINE.matcher(result).replaceAll("");
        result = EN_PAGE_LINE.matcher(result).replaceAll("");
        return result;
    }

    @Override
    public int order() {
        return 20;
    }
}
