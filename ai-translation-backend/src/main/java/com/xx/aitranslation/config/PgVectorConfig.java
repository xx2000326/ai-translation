package com.xx.aitranslation.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

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

    @Value("${app.pgvector.dimensions:1536}")
    private int dimensions;

    @Value("${app.pgvector.initialize-schema:true}")
    private boolean initializeSchema;

    @Bean
    public VectorStore vectorStore(@Qualifier("pgVectorJdbcTemplate") JdbcTemplate pgVectorJdbcTemplate,
                                   EmbeddingModel embeddingModel) {
        return PgVectorStore.builder(pgVectorJdbcTemplate, embeddingModel)
                .schemaName("public")
                .vectorTableName(tableName)
                .dimensions(dimensions)
                .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                .indexType(PgVectorStore.PgIndexType.HNSW)
                .initializeSchema(initializeSchema)
                .build();
    }
}
