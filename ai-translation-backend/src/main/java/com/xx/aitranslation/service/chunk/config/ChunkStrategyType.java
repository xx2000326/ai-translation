package com.xx.aitranslation.service.chunk.config;

import com.xx.aitranslation.common.BizException;
import org.springframework.util.ObjectUtils;

/**
 * 文档拆分策略类型。
 */
public enum ChunkStrategyType {

    /** 固定长度拆分（按字符数 + Overlap）。 */
    FIXED_SIZE,

    /** 按段落（空行分隔）拆分。 */
    PARAGRAPH,

    /** 按句子（中英文标点）拆分。 */
    SENTENCE,

    /** 按 Markdown 标题层级拆分。 */
    MARKDOWN,

    /** 按标题结构层级拆分（构建标题树，适合 Word / PDF）。 */
    TITLE,

    /** 父子层级拆分（Parent / Child），适合超大文件。 */
    HIERARCHICAL;

    /**
     * 解析策略名称：为空或 {@code AUTO} 时返回 {@code null}（由引擎按文件类型自动选择），
     * 无法识别时抛出业务异常。
     *
     * @param strategy 策略名称（不区分大小写）
     * @return 匹配的策略类型，自动选择时为 {@code null}
     */
    public static ChunkStrategyType parse(String strategy) {
        if (ObjectUtils.isEmpty(strategy) || "AUTO".equalsIgnoreCase(strategy.trim())) {
            return null;
        }
        try {
            return valueOf(strategy.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BizException("chunk.strategy.unsupported");
        }
    }
}
