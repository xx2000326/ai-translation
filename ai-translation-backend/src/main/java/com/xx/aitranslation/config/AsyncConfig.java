package com.xx.aitranslation.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 异步执行配置：
 * <ul>
 *     <li>{@code taskExecutor}：编排级线程池，承载文档解析 / 翻译编排 / RAG 写入等任务（每个任务占一个线程）。</li>
 *     <li>{@code translationExecutor}：句子级并发翻译线程池，并发度由 {@code app.translation.concurrency} 控制（V1 模块四多 Agent 并发）。</li>
 * </ul>
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /** 句子级并行翻译并发度（V1 模块四：多 Agent 并发翻译） */
    @Value("${app.translation.concurrency:8}")
    private int translationConcurrency;

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

    /**
     * 句子级并发翻译线程池：核心 / 最大均为并发度，调用方阻塞等待所有句子翻译完成，
     * 因此用 CallerRunsPolicy 兜底，避免任务被拒。
     */
    @Bean("translationExecutor")
    public Executor translationExecutor() {
        int size = translationConcurrency <= 0 ? 1 : translationConcurrency;
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(size);
        executor.setMaxPoolSize(size);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("ai-trans-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
