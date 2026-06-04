package com.xx.aitranslation.service;

import com.xx.aitranslation.entity.TranslationSegment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * RAG 记忆服务：基于 PGVector 存储与检索历史翻译，增强一致性与上下文能力。
 * <p>
 * 这里使用 SpringAI 的 {@link VectorStore} 抽象，role / style 等业务字段保存在 metadata 中，
 * 检索时按 role + style 过滤，提升相似翻译的相关性。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {

    /** 相似翻译检索 TopK */
    private static final int TOP_K = 3;
    /** DashScope text-embedding 单批上限 */
    private static final int EMBEDDING_BATCH_SIZE = 10;
    /** 相似度阈值，过滤掉相关性过低的记忆 */
    private static final double SIMILARITY_THRESHOLD = 0.5;

    private static final String META_USER = "customerId";
    private static final String META_ROLE = "role";
    private static final String META_STYLE = "style";

    private final VectorStore vectorStore;

    /**
     * 写入翻译记忆（向量化由 EmbeddingModel 在 VectorStore 内部完成），按客户隔离。
     *
     * @param content 入库的文本（一般为原文，便于后续按语义检索）
     */
    public void saveMemory(String content, Long customerId, String role, String style) {
        if (ObjectUtils.isEmpty(content)) {
            return;
        }
        try {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put(META_USER, customerId == null ? "" : String.valueOf(customerId));
            metadata.put(META_ROLE, role);
            metadata.put(META_STYLE, style);
            Document document = new Document(content, metadata);
            vectorStore.add(List.of(document));
        } catch (Exception e) {
            // RAG 写入失败不应阻断主翻译流程
            log.warn("写入翻译记忆失败: {}", e.getMessage());
        }
    }

    /**
     * 异步批量将人工审校确认后的段落翻译对写入向量库，供后续翻译任务检索参考。
     * <p>
     * 每个段落以"原文：xxx\n译文：xxx"的格式存储，保证语义检索时能同时命中原文与译文。
     * 原文或最终译文为空的段落跳过，不影响整体流程。
     *
     * @param segments   已完成审校的段落列表
     * @param customerId 客户ID（用于按客户隔离检索）
     * @param role       翻译角色
     * @param style      翻译风格
     */
    @Async("taskExecutor")
    public void saveTranslationMemoriesAsync(List<TranslationSegment> segments,
                                             Long customerId, String role, String style) {
        if (ObjectUtils.isEmpty(segments)) {
            return;
        }
        List<Document> documents = new ArrayList<>();
        for (TranslationSegment seg : segments) {
            if (ObjectUtils.isEmpty(seg.getOriginalText()) || ObjectUtils.isEmpty(seg.getFinalText())) {
                continue;
            }
            String content = "原文：" + seg.getOriginalText() + "\n译文：" + seg.getFinalText();
            Map<String, Object> metadata = new HashMap<>();
            metadata.put(META_USER, customerId == null ? "" : String.valueOf(customerId));
            metadata.put(META_ROLE, ObjectUtils.isEmpty(role) ? "" : role);
            metadata.put(META_STYLE, ObjectUtils.isEmpty(style) ? "" : style);
            documents.add(new Document(content, metadata));
        }
        if (documents.isEmpty()) {
            return;
        }
        // DashScope text-embedding 单批上限为 10，分批写入避免 400 错误
        List<List<Document>> batches = IntStream.range(0, (documents.size() + EMBEDDING_BATCH_SIZE - 1) / EMBEDDING_BATCH_SIZE)
                .mapToObj(i -> documents.subList(i * EMBEDDING_BATCH_SIZE,
                        Math.min((i + 1) * EMBEDDING_BATCH_SIZE, documents.size())))
                .collect(Collectors.toList());
        int saved = 0;
        for (List<Document> batch : batches) {
            try {
                vectorStore.add(batch);
                saved += batch.size();
            } catch (Exception e) {
                log.warn("批量写入翻译记忆失败（第 {}/{} 批）", batches.indexOf(batch) + 1, batches.size(), e);
            }
        }
        log.info("已将 {}/{} 个翻译段落写入 RAG 记忆库", saved, documents.size());
    }

    /**
     * 检索与输入文本语义相似的历史翻译（限定当前客户），构建注入 Prompt 的参考上下文。
     *
     * @return 多行历史参考文本，无命中时返回空串
     */
    public String buildRagContext(String text, Long customerId, String role, String style) {
        if (ObjectUtils.isEmpty(text)) {
            return "";
        }
        try {
            FilterExpressionBuilder fb = new FilterExpressionBuilder();
            Filter.Expression filter = fb.and(
                    fb.eq(META_USER, customerId == null ? "" : String.valueOf(customerId)),
                    fb.and(fb.eq(META_ROLE, role), fb.eq(META_STYLE, style))
            ).build();

            SearchRequest request = SearchRequest.builder()
                    .query(text)
                    .topK(TOP_K)
                    .similarityThreshold(SIMILARITY_THRESHOLD)
                    .filterExpression(filter)
                    .build();

            List<Document> documents = vectorStore.similaritySearch(request);
            if (ObjectUtils.isEmpty(documents)) {
                return "";
            }
            return documents.stream()
                    .map(Document::getText)
                    .filter(t -> !ObjectUtils.isEmpty(t))
                    .collect(Collectors.joining("\n"));
        } catch (Exception e) {
            log.warn("检索翻译记忆失败: {}", e.getMessage());
            return "";
        }
    }
}
