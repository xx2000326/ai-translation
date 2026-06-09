package com.xx.aitranslation.service.parse;

import org.springframework.util.ObjectUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 标题层级识别（高级拆分）。
 *
 * <p>识别规则（返回值为层级，0 表示正文）：
 * <ul>
 *     <li>第X章 → 1</li>
 *     <li>第X节 → 2</li>
 *     <li>数字编号：1 → 1，1.1 → 2，1.1.1 → 3 ...（按层级点号个数 + 1）</li>
 * </ul>
 * 数字编号标题要求整行较短，避免误伤以数字开头的正文。
 */
final class HeadingDetector {

    /** 数字编号标题的最大行长度，超出视为正文。 */
    private static final int MAX_HEADING_LENGTH = 60;

    private static final Pattern CHAPTER =
            Pattern.compile("^第\\s*[0-9一二三四五六七八九十百千零两]+\\s*章([\\s、：:.．]|$).*");

    private static final Pattern SECTION =
            Pattern.compile("^第\\s*[0-9一二三四五六七八九十百千零两]+\\s*节([\\s、：:.．]|$).*");

    /** 形如 1 / 1.1 / 1.1.1，编号后可跟空白、顿号、冒号或标题文字。 */
    private static final Pattern NUMBERED =
            Pattern.compile("^(\\d+(?:\\.\\d+)*)\\.?(?:[\\s、：:．].*)?$");

    /** 样式名中的标题层级，如 Heading1 / heading 2 / 标题 3。 */
    private static final Pattern STYLE_HEADING =
            Pattern.compile("(?i)(?:heading|标题)\\s*([1-9])");

    private HeadingDetector() {
    }

    /**
     * 按文本识别标题层级，0 表示正文。
     */
    static int detectLevel(String text) {
        if (ObjectUtils.isEmpty(text)) {
            return 0;
        }
        String trimmed = text.trim();
        if (CHAPTER.matcher(trimmed).matches()) {
            return 1;
        }
        if (SECTION.matcher(trimmed).matches()) {
            return 2;
        }
        if (trimmed.length() <= MAX_HEADING_LENGTH) {
            Matcher matcher = NUMBERED.matcher(trimmed);
            if (matcher.matches()) {
                String number = matcher.group(1);
                int dots = (int) number.chars().filter(c -> c == '.').count();
                return dots + 1;
            }
        }
        return 0;
    }

    /**
     * 按样式名（如 DOCX 的 {@code Heading1} / {@code 标题 1}）识别标题层级，0 表示非标题样式。
     */
    static int styleLevel(String styleName) {
        if (ObjectUtils.isEmpty(styleName)) {
            return 0;
        }
        Matcher matcher = STYLE_HEADING.matcher(styleName);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return 0;
    }
}
