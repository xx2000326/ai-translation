package com.xx.aitranslation.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 异步执行配置：
 * <ul>
 *     <li>{@code taskExecutor}：编排级线程池，承载文档解析 / 翻译编排 / RAG 写入等任务（每个任务占一个线程）。</li>
 *     <li>句子级并发翻译由 {@link com.xx.aitranslation.service.pipeline.SentenceTranslationQueue} 负责。</li>
 *     <li>审校批次并发由 {@link com.xx.aitranslation.service.pipeline.ReviewBatchQueue} 负责。</li>
 * </ul>
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 编排级任务线程池：核心 4 / 最大 8 / 队列 100，线程名前缀 ai-task-。
     */
    @Bean("taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("ai-task-");
        executor.initialize();
        return executor;
    }
}
