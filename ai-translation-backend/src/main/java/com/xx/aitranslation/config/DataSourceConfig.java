package com.xx.aitranslation.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * 双数据源配置：
 * <ul>
 *     <li>MySQL（主数据源）：承载用户 / 术语库 / 翻译历史等业务数据，供 MyBatis-Plus 使用。</li>
 *     <li>PostgreSQL + pgvector：承载 RAG 翻译记忆向量，供 SpringAI {@code PgVectorStore} 使用。</li>
 * </ul>
 * 注意：一旦手动声明 DataSource，SpringBoot 的自动数据源装配会退避，因此两个数据源均需显式声明。
 */
@Configuration
public class DataSourceConfig {

    // ===================== MySQL 主数据源 =====================
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties mysqlDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    public DataSource dataSource(@Qualifier("mysqlDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    // ===================== PostgreSQL(pgvector) 数据源 =====================
    @Bean
    @ConfigurationProperties("app.pgvector")
    public DataSourceProperties pgVectorDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "pgVectorDataSource")
    public DataSource pgVectorDataSource(@Qualifier("pgVectorDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    @Bean(name = "pgVectorJdbcTemplate")
    public JdbcTemplate pgVectorJdbcTemplate(@Qualifier("pgVectorDataSource") DataSource pgVectorDataSource) {
        return new JdbcTemplate(pgVectorDataSource);
    }
}
