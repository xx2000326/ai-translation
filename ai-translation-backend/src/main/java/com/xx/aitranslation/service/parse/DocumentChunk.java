package com.xx.aitranslation.service.parse;

/**
 * 高级拆分输出单元（Chunk）。
 *
 * <p>保留章节层级与父子关系，支持后续情感分析、术语抽取、RAG 检索与翻译任务调度。
 *
 * @param chunkId     Chunk 唯一标识（文档内）
 * @param title       Chunk 标题（章节标题，或超长切分后的 {@code 标题-PartN}）
 * @param parentTitle 父标题（层级父章节标题；超长切分的 Part 为其所属章节标题）
 * @param level       章节层级（根级正文为 0）
 * @param content     Chunk 正文
 */
public record DocumentChunk(
        String chunkId,
        String title,
        String parentTitle,
        Integer level,
        String content) {
}
