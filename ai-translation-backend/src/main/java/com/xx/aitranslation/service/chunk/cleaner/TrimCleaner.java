package com.xx.aitranslation.service.chunk.cleaner;

import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

/**
 * 首尾空白清洗：去除整篇文档首尾的空白（最后执行）。
 */
@Component
public class TrimCleaner implements DocumentCleaner {

    @Override
    public String clean(String content) {
        if (ObjectUtils.isEmpty(content)) {
            return content;
        }
        return content.strip();
    }

    @Override
    public int order() {
        return 100;
    }
}
