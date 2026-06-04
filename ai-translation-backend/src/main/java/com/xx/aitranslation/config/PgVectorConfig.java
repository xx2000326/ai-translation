package com.xx.aitranslation.config;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * PGVector 向量库配置（RAG 翻译记忆）。
 * <p>
 * 显式声明 {@link VectorStore} Bean，使其使用独立的 PostgreSQL 数据源
 * （{@code pgVectorJdbcTemplate}），与业务库 MySQL 解耦；同时屏蔽 SpringAI 默认
 * 基于主数据源的 PgVectorStore 自动装配。
 */
@Configuration
public class PgVectorConfig {

    @Value("${app.pgvector.table-name:translation_memory}")
    private String tableName;

    @Value("${app.pgvector.dimensions:1024}")
    private int dimensions;

    @Value("${app.pgvector.initialize-schema:true}")
    private boolean initializeSchema;

    /** DashScope text-embedding 单批上限 */
    private static final int EMBEDDING_BATCH_SIZE = 10;

    /**
     * 固定批次大小的 BatchingStrategy，防止 DashScope 批次超限（最大 10 条）。
     */
    @Bean
    public BatchingStrategy fixedSizeBatchingStrategy() {
        return documents -> {
            List<List<Document>> batches = new ArrayList<>();
            for (int i = 0; i < documents.size(); i += EMBEDDING_BATCH_SIZE) {
                batches.add(documents.subList(i, Math.min(i + EMBEDDING_BATCH_SIZE, documents.size())));
            }
            return batches;
        };
    }

    @Bean
    public VectorStore vectorStore(@Qualifier("pgVectorJdbcTemplate") JdbcTemplate pgVectorJdbcTemplate,
                                   EmbeddingModel embeddingModel,
                                   BatchingStrategy fixedSizeBatchingStrategy) {
        return PgVectorStore.builder(pgVectorJdbcTemplate, embeddingModel)
                .schemaName("public")
                .vectorTableName(tableName)
                .dimensions(dimensions)
                .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                .indexType(PgVectorStore.PgIndexType.HNSW)
                .initializeSchema(initializeSchema)
                .batchingStrategy(fixedSizeBatchingStrategy)
                .build();
    }
}
