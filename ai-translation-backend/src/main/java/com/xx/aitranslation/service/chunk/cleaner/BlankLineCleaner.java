package com.xx.aitranslation.service.chunk.cleaner;

import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.regex.Pattern;

/**
 * 空行清洗：将连续多个空行压缩为单个空行，并去除每行行尾空白。
 */
@Component
public class BlankLineCleaner implements DocumentCleaner {

    /** 行尾空白。 */
    private static final Pattern TRAILING_SPACES = Pattern.compile("(?m)[ \\t]+$");

    /** 三个及以上换行 → 两个换行（即保留一个空行）。 */
    private static final Pattern MULTI_BLANK_LINES = Pattern.compile("\\n{3,}");

    @Override
    public String clean(String content) {
        if (ObjectUtils.isEmpty(content)) {
            return content;
        }
        String result = TRAILING_SPACES.matcher(content).replaceAll("");
        result = MULTI_BLANK_LINES.matcher(result).replaceAll("\n\n");
        return result;
    }

    @Override
    public int order() {
        return 30;
    }
}
