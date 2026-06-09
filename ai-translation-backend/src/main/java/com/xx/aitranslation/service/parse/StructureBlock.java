package com.xx.aitranslation.service.parse;

/**
 * 高级拆分的输入块：一段已清洗的源文本及其标题层级。
 *
 * <p>{@code headingLevel} 为 0 表示正文，&gt;0 表示标题层级。各格式解析器（DOCX/PDF...）
 * 负责把文档段落转换为有序的 {@code StructureBlock} 列表，再交给
 * {@link DocumentStructureSplitter} 构建章节树并产出 {@link DocumentChunk}。
 *
 * @param text         已清洗的段落文本
 * @param headingLevel 标题层级，0 表示正文
 */
public record StructureBlock(String text, int headingLevel) {
}
