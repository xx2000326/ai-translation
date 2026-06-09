package com.xx.aitranslation.service.pipeline;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 可选的 LLM API 速率限制器（按每分钟调用次数）。
 * <p>
 * {@code rate-limit-per-minute <= 0} 时不限制。
 */
@Component
public class TranslationRateLimiter {

    private final boolean enabled;
    private final long intervalNanos;
    private final Object lock = new Object();
    private long lastAcquireNanos;

    public TranslationRateLimiter(
            @Value("${app.translation.rate-limit-per-minute:0}") int rateLimitPerMinute) {
        this.enabled = rateLimitPerMinute > 0;
        this.intervalNanos = enabled ? 60_000_000_000L / rateLimitPerMinute : 0L;
    }

    /**
     * 获取调用许可，必要时阻塞等待。
     */
    public void acquire() {
        if (!enabled) {
            return;
        }
        synchronized (lock) {
            long now = System.nanoTime();
            long waitNanos = lastAcquireNanos + intervalNanos - now;
            if (waitNanos > 0) {
                sleepNanos(waitNanos);
            }
            lastAcquireNanos = System.nanoTime();
        }
    }

    private static void sleepNanos(long waitNanos) {
        long millis = waitNanos / 1_000_000;
        int nanos = (int) (waitNanos % 1_000_000);
        try {
            Thread.sleep(millis, nanos);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Rate limiter interrupted", e);
        }
    }
}
