package com.xx.aitranslation.service.chunk.support;

import org.springframework.util.ObjectUtils;

import java.util.ArrayList;
import java.util.List;

/** 章节内按段落贪心切 Part；单段超长则字符硬切。 */
public final class SectionPartSplitter {

    private SectionPartSplitter() {
    }

    public static List<String> splitByParagraphs(List<String> paragraphs, int maxBodySize) {
        int budget = Math.max(1, maxBodySize);
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String para : paragraphs) {
            if (ObjectUtils.isEmpty(para)) {
                continue;
            }
            if (para.length() > budget) {
                flush(current, parts);
                for (int i = 0; i < para.length(); i += budget) {
                    parts.add(para.substring(i, Math.min(para.length(), i + budget)));
                }
                continue;
            }
            if (current.length() > 0 && current.length() + para.length() + 1 > budget) {
                flush(current, parts);
            }
            if (current.length() > 0) {
                current.append('\n');
            }
            current.append(para);
        }
        flush(current, parts);
        return parts;
    }

    /** 将 content 按换行拆段后切 Part。 */
    public static List<String> splitContent(String content, int maxBodySize) {
        if (ObjectUtils.isEmpty(content)) {
            return List.of();
        }
        List<String> paragraphs = new ArrayList<>();
        for (String line : content.split("\n", -1)) {
            if (!ObjectUtils.isEmpty(line)) {
                paragraphs.add(line);
            }
        }
        return splitByParagraphs(paragraphs, maxBodySize);
    }

    private static void flush(StringBuilder buffer, List<String> parts) {
        if (buffer.length() > 0) {
            parts.add(buffer.toString());
            buffer.setLength(0);
        }
    }
}
