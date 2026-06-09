package com.xx.aitranslation.service.chunk.support;

import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

/**
 * 轻量级 token 估算器（不依赖具体模型分词）。
 * <p>
 * 经验规则：CJK 字符约 1 字 1 token；其余（拉丁字母、数字、空白等）约每 4 个字符 1 token。
 * 仅用于拆分参数参考，非精确计费值。
 */
@Component
public class TokenCounter {

    private static final double NON_CJK_CHARS_PER_TOKEN = 4.0;

    /**
     * 估算文本 token 数。
     */
    public int estimate(String text) {
        if (ObjectUtils.isEmpty(text)) {
            return 0;
        }
        int cjk = 0;
        int other = 0;
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            if (isCjk(codePoint)) {
                cjk++;
            } else if (!Character.isWhitespace(codePoint)) {
                other++;
            }
            i += Character.charCount(codePoint);
        }
        return cjk + (int) Math.ceil(other / NON_CJK_CHARS_PER_TOKEN);
    }

    private boolean isCjk(int codePoint) {
        Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
        return script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA
                || script == Character.UnicodeScript.HANGUL;
    }
}
