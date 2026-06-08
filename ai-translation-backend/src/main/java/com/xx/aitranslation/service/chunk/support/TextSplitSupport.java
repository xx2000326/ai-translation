package com.xx.aitranslation.service.chunk.support;

import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文本切分基础能力：固定窗口（含 Overlap）、按段落、按句子，供各拆分策略复用。
 */
@Component
public class TextSplitSupport {

    /** 段落分隔：一个及以上空行。 */
    private static final Pattern PARAGRAPH_DELIMITER = Pattern.compile("\\n\\s*\\n");

    /** 句子结束标点（中英文）：句号、问号、感叹号、分号等，保留标点。 */
    private static final Pattern SENTENCE_BOUNDARY =
            Pattern.compile("(?<=[。！？!?；;])|(?<=[.][\\s])");

    /**
     * 固定窗口切分，支持 Overlap（相邻块冗余重叠 overlap 个字符）。
     *
     * @param content   原文
     * @param chunkSize 窗口字符数
     * @param overlap   相邻窗口重叠字符数（自动钳制为 [0, chunkSize-1]）
     */
    public List<String> fixedWindows(String content, int chunkSize, int overlap) {
        List<String> result = new ArrayList<>();
        if (ObjectUtils.isEmpty(content)) {
            return result;
        }
        int size = Math.max(1, chunkSize);
        // overlap 不得 >= size，否则窗口无法前进；同时不得为负
        int safeOverlap = Math.min(Math.max(0, overlap), size - 1);
        int step = size - safeOverlap;
        int length = content.length();
        for (int start = 0; start < length; start += step) {
            int end = Math.min(start + size, length);
            result.add(content.substring(start, end));
            if (end >= length) {
                break;
            }
        }
        return result;
    }

    /**
     * 以句子为最小单位、按目标字符数"就近"装箱：累计句子直到长度达到 {@code targetSize} 即收口，
     * 始终在完整句子边界结束，因此单块长度可超过 {@code targetSize}（字符数仅作参考，对翻译更友好）。
     *
     * @param content    原文
     * @param targetSize 参考字符数
     * @return 句子边界对齐的文本块
     */
    public List<String> packBySentence(String content, int targetSize) {
        List<String> result = new ArrayList<>();
        List<String> sentences = bySentence(content);
        if (sentences.isEmpty()) {
            return result;
        }
        int target = Math.max(1, targetSize);
        StringBuilder current = new StringBuilder();
        for (String sentence : sentences) {
            if (current.length() == 0) {
                current.append(sentence);
            } else {
                current.append(' ').append(sentence);
            }
            if (current.length() >= target) {
                result.add(current.toString());
                current.setLength(0);
            }
        }
        if (current.length() > 0) {
            result.add(current.toString());
        }
        return result;
    }

    /**
     * 按句子为相邻文本块补充 Overlap：自第二块起，取上一块结尾若干"完整句子"（累计长度达到 {@code overlap} 即止）拼到块首。
     * 冗余以句子为单位，可超过 {@code overlap} 字符，避免在句子中间断裂。
     *
     * @param blocks  原始文本块
     * @param overlap 参考重叠字符数
     * @return 带句子级重叠的文本块（块数不变）
     */
    public List<String> applySentenceOverlap(List<String> blocks, int overlap) {
        List<String> result = new ArrayList<>();
        if (ObjectUtils.isEmpty(blocks)) {
            return result;
        }
        if (overlap <= 0 || blocks.size() < 2) {
            result.addAll(blocks);
            return result;
        }
        result.add(blocks.get(0));
        for (int i = 1; i < blocks.size(); i++) {
            String tail = trailingSentences(blocks.get(i - 1), overlap);
            String current = blocks.get(i);
            result.add(tail.isEmpty() ? current : tail + " " + current);
        }
        return result;
    }

    /**
     * 取文本结尾的若干完整句子，累计长度达到 {@code minChars} 即止（保持原顺序）。
     */
    private String trailingSentences(String text, int minChars) {
        List<String> sentences = bySentence(text);
        if (sentences.isEmpty()) {
            return "";
        }
        int total = 0;
        int start = sentences.size();
        for (int i = sentences.size() - 1; i >= 0; i--) {
            start = i;
            total += sentences.get(i).length();
            if (total >= minChars) {
                break;
            }
        }
        return String.join(" ", sentences.subList(start, sentences.size()));
    }

    /**
     * 按段落切分，过滤空白段。
     * <p>
     * 优先以空行（{@code \n\n} 或连续空白行）为分隔符；若整篇不含空行（如 PDF/Word 抽取文本或单换行分段），
     * 则回退为按单个换行符切分，避免整篇被当作一个段落而无法拆分。
     */
    public List<String> byParagraph(String content) {
        List<String> result = new ArrayList<>();
        if (ObjectUtils.isEmpty(content)) {
            return result;
        }
        for (String part : PARAGRAPH_DELIMITER.split(content)) {
            String trimmed = part.strip();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        if (result.size() > 1) {
            return result;
        }
        // 回退：无空行分段时，按单换行切分
        List<String> byLine = new ArrayList<>();
        for (String line : content.split("\n")) {
            String trimmed = line.strip();
            if (!trimmed.isEmpty()) {
                byLine.add(trimmed);
            }
        }
        return byLine.size() > result.size() ? byLine : result;
    }

    /**
     * 按句子切分（中英文标点边界），过滤空白句。
     */
    public List<String> bySentence(String content) {
        List<String> result = new ArrayList<>();
        if (ObjectUtils.isEmpty(content)) {
            return result;
        }
        for (String line : content.split("\\n")) {
            if (line.strip().isEmpty()) {
                continue;
            }
            Matcher matcher = SENTENCE_BOUNDARY.matcher(line);
            int last = 0;
            while (matcher.find()) {
                int end = matcher.end();
                if (end > last) {
                    String sentence = line.substring(last, end).strip();
                    if (!sentence.isEmpty()) {
                        result.add(sentence);
                    }
                    last = end;
                }
            }
            if (last < line.length()) {
                String tail = line.substring(last).strip();
                if (!tail.isEmpty()) {
                    result.add(tail);
                }
            }
        }
        return result;
    }
}
