package com.xx.aitranslation.service.parse;

/**
 * 文档解析后的单个内容块。
 *
 * @param order 块顺序号（从 0 递增，用于还原原文顺序）
 * @param type  块类型，如 paragraph / heading / list
 * @param text  块文本内容
 */
public record ParsedBlock(int order, String type, String text) {
}
