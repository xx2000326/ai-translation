package com.xx.aitranslation;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 排除 PGVector 自动配置：其会基于主数据源(MySQL)注册一个名为 vectorStore 的 Bean，
 * 与 {@link com.xx.aitranslation.config.PgVectorConfig} 中基于独立 PG 数据源的 vectorStore 冲突。
 */
@SpringBootApplication(exclude = PgVectorStoreAutoConfiguration.class)
@MapperScan("com.xx.aitranslation.mapper")
public class AiTranslationApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiTranslationApplication.class, args);
    }

}
