-- ============================================================
-- AI 翻译助手 - PostgreSQL + pgvector 向量库（参考脚本）
-- 说明：应用启动时 SpringAI PgVectorStore 会按 app.pgvector.initialize-schema
--      自动创建 vector 扩展、translation_memory 表与 HNSW 索引。
--      下方脚本仅供手动初始化或了解表结构参考。
-- ============================================================

CREATE EXTENSION IF NOT EXISTS vector;

-- SpringAI PgVectorStore 标准表结构（表名由 app.pgvector.table-name 指定）
CREATE TABLE IF NOT EXISTS translation_memory (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content TEXT,                 -- 原文/翻译内容
    metadata JSONB,               -- 业务元数据（role / style 等）
    embedding VECTOR(1536)        -- 内容向量（与 embedding 模型维度一致）
);

CREATE INDEX IF NOT EXISTS translation_memory_embedding_idx
    ON translation_memory USING HNSW (embedding vector_cosine_ops);
