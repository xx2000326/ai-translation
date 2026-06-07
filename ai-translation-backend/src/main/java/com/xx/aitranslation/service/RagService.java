package com.xx.aitranslation.service;

import com.xx.aitranslation.entity.TranslationSentence;
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
 * 这里使用 SpringAI 的 {@link VectorStore} 抽象，business 字段统一保存在 metadata：
 * {@code customerId + role + style + sourceLang + targetLang}，检索时按这五个维度过滤，
 * 保证单句翻译与文档流水线写入 / 检索的记忆维度一致、可互相复用。
 * 入库内容统一为"原文：xxx\n译文：xxx"翻译对，便于按语义检索时同时命中原文与译文。
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
    private static final String META_SOURCE_LANG = "sourceLang";
    private static final String META_TARGET_LANG = "targetLang";

    private final VectorStore vectorStore;

    /**
     * 写入单条翻译记忆（原文 + 译文翻译对），按 customerId + role + style + 语言方向隔离。
     *
     * @param original   原文
     * @param translated 译文
     */
    public void saveMemory(String original, String translated, Long customerId,
                           String role, String style, String sourceLang, String targetLang) {
        if (ObjectUtils.isEmpty(original) || ObjectUtils.isEmpty(translated)) {
            return;
        }
        try {
            Document document = new Document(buildPairContent(original, translated),
                    buildMetadata(customerId, role, style, sourceLang, targetLang));
            vectorStore.add(List.of(document));
        } catch (Exception e) {
            // RAG 写入失败不应阻断主翻译流程
            log.warn("写入翻译记忆失败: {}", e.getMessage());
        }
    }

    /**
     * 异步批量将段落翻译对写入向量库，供后续翻译任务检索参考。
     * <p>
     * 每个段落以"原文：xxx\n译文：xxx"的格式存储；原文或最终译文为空的段落跳过，不影响整体流程。
     */
    @Async("taskExecutor")
    public void saveTranslationMemoriesAsync(List<TranslationSentence> sentences, Long customerId,
                                             String role, String style, String sourceLang, String targetLang) {
        if (ObjectUtils.isEmpty(sentences)) {
            return;
        }
        Map<String, Object> metadata = buildMetadata(customerId, role, style, sourceLang, targetLang);
        List<Document> documents = new ArrayList<>();
        for (TranslationSentence sent : sentences) {
            String finalText = resolveTranslated(sent);
            if (ObjectUtils.isEmpty(sent.getOriginalText()) || ObjectUtils.isEmpty(finalText)) {
                continue;
            }
            documents.add(new Document(buildPairContent(sent.getOriginalText(), finalText), new HashMap<>(metadata)));
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
        log.info("已将 {}/{} 个翻译句子写入 RAG 记忆库", saved, documents.size());
    }

    /**
     * 检索与输入文本语义相似的历史翻译（按 customerId + role + style + 语言方向过滤），构建注入 Prompt 的参考上下文。
     *
     * @return 多行历史参考文本，无命中时返回空串
     */
    public String buildRagContext(String text, Long customerId, String role, String style,
                                  String sourceLang, String targetLang) {
        if (ObjectUtils.isEmpty(text)) {
            return "";
        }
        try {
            FilterExpressionBuilder fb = new FilterExpressionBuilder();
            Filter.Expression filter = fb.and(
                    fb.eq(META_USER, normalize(customerId)),
                    fb.and(
                            fb.and(fb.eq(META_ROLE, normalize(role)), fb.eq(META_STYLE, normalize(style))),
                            fb.and(fb.eq(META_SOURCE_LANG, normalize(sourceLang)), fb.eq(META_TARGET_LANG, normalize(targetLang)))
                    )
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

    private Map<String, Object> buildMetadata(Long customerId, String role, String style,
                                              String sourceLang, String targetLang) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put(META_USER, normalize(customerId));
        metadata.put(META_ROLE, normalize(role));
        metadata.put(META_STYLE, normalize(style));
        metadata.put(META_SOURCE_LANG, normalize(sourceLang));
        metadata.put(META_TARGET_LANG, normalize(targetLang));
        return metadata;
    }

    private String buildPairContent(String original, String translated) {
        return "原文：" + original + "\n译文：" + translated;
    }

    private String resolveTranslated(TranslationSentence sent) {
        if (!ObjectUtils.isEmpty(sent.getFinalText())) {
            return sent.getFinalText();
        }
        if (!ObjectUtils.isEmpty(sent.getReviewedText())) {
            return sent.getReviewedText();
        }
        return sent.getTranslatedText();
    }

    private String normalize(Object value) {
        return ObjectUtils.isEmpty(value) ? "" : String.valueOf(value);
    }
}
