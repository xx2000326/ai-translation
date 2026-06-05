package com.xx.aitranslation.service.parse;

import java.util.List;

/**
 * 解析结果：文件 → 段落 → 句子（对齐 yunshu MyParagraph / MySentence 结构）。
 */
public record ParsedDocument(List<ParsedParagraph> paragraphs) {
}
