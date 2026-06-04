package com.xx.aitranslation.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 异步执行配置：为文档解析 / 翻译 / 审校等耗时流程提供独立线程池。
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 任务执行线程池：核心 3 / 最大 6 / 队列 100，线程名前缀 ai-task-。
     */
    @Bean("taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(3);
        executor.setMaxPoolSize(6);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("ai-task-");
        executor.initialize();
        return executor;
    }
}
