package com.xx.aitranslation.enums;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * 解析拆分粒度：决定解析时翻译单元的颗粒度。
 * <ul>
 *     <li>{@link #SENTENCE}：按句拆分（SRX 分句），每句一个翻译单元（默认）。</li>
 *     <li>{@link #PARAGRAPH}：按段拆分，每个段落整体作为一个翻译单元，不再细分句子。</li>
 *     <li>{@link #STRUCTURE}：高级拆分（按文档结构）。识别标题层级构建章节树，以最小章节节点作为
 *     Chunk；超长 Chunk 再按段落切分为 Part，并保留章节层级与父子关系。</li>
 * </ul>
 */
public enum ParseGranularity {

    SENTENCE,
    PARAGRAPH,
    STRUCTURE;

    public static List<String> codes() {
        return Arrays.stream(values()).map(Enum::name).toList();
    }

    /**
     * 按名解析粒度，空或未匹配时回退默认 {@link #SENTENCE}。
     */
    public static ParseGranularity fromCode(String code) {
        if (Objects.isNull(code)) {
            return SENTENCE;
        }
        return Arrays.stream(values())
                .filter(g -> g.name().equalsIgnoreCase(code))
                .findFirst()
                .orElse(SENTENCE);
    }
}
