package com.xx.aitranslation.service.chunk.cleaner;

import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

/**
 * Unicode 归一化：统一换行符、去除零宽字符与 BOM、将全角空格归一。
 */
@Component
public class UnicodeCleaner implements DocumentCleaner {

    @Override
    public String clean(String content) {
        if (ObjectUtils.isEmpty(content)) {
            return content;
        }
        return content
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                // BOM / 零宽字符
                .replace("\uFEFF", "")
                .replace("\u200B", "")
                .replace("\u200C", "")
                .replace("\u200D", "")
                // 全角空格 / 不间断空格 归一为普通空格
                .replace('\u3000', ' ')
                .replace('\u00A0', ' ');
    }

    @Override
    public int order() {
        return 10;
    }
}
