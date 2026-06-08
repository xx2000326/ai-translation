package com.xx.aitranslation.service.pipeline;

import com.xx.aitranslation.agent.SummaryAgent;
import com.xx.aitranslation.common.BizException;
import com.xx.aitranslation.dto.SummaryBatch;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntConsumer;

/**
 * 风格统一批次全局有界队列：多 Worker 并行调用 {@link SummaryAgent#unifyBatch}。
 * <p>
 * 调度模式与 {@link ReviewBatchQueue} 一致，共享 {@link TranslationRateLimiter} 与并发配置。
 */
@Slf4j
@Service
public class SummaryBatchQueue {

    private final SummaryAgent summaryAgent;
    private final TranslationRateLimiter rateLimiter;

    @Value("${app.translation.concurrency:8}")
    private int concurrency;

    @Value("${app.translation.queue-capacity:16}")
    private int queueCapacity;

    private BlockingQueue<WorkItem> queue;
    private volatile boolean running = true;

    public SummaryBatchQueue(@Lazy SummaryAgent summaryAgent, TranslationRateLimiter rateLimiter) {
        this.summaryAgent = summaryAgent;
        this.rateLimiter = rateLimiter;
    }

    @PostConstruct
    void startWorkers() {
        int workers = concurrency <= 0 ? 1 : concurrency;
        int capacity = queueCapacity <= 0 ? workers * 2 : queueCapacity;
        queue = new ArrayBlockingQueue<>(capacity);
        for (int i = 0; i < workers; i++) {
            Thread.startVirtualThread(this::workerLoop);
        }
        log.info("Summary batch queue started: workers={}, capacity={}", workers, capacity);
    }

    @PreDestroy
    void shutdown() {
        running = false;
        int workers = concurrency <= 0 ? 1 : concurrency;
        for (int i = 0; i < workers; i++) {
            queue.offer(WorkItem.POISON);
        }
    }

    /**
     * 并行风格统一：阻塞等待全部批次完成并合并主写入区间结果。
     */
    public Map<Integer, String> executeUnify(
            List<SummaryBatch> batches,
            String styleGuide,
            String requirement,
            String sourceLang,
            String targetLang,
            String modelCode,
            IntConsumer onPrimarySentencesDone) {
        if (ObjectUtils.isEmpty(batches)) {
            return Map.of();
        }

        CountDownLatch latch = new CountDownLatch(batches.size());
        AtomicReference<Throwable> error = new AtomicReference<>();
        Map<Integer, String> merged = new ConcurrentHashMap<>();

        for (SummaryBatch batch : batches) {
            WorkItem item = new WorkItem(
                    batch, styleGuide, requirement, sourceLang, targetLang, modelCode,
                    merged, latch, onPrimarySentencesDone, error);
            try {
                queue.put(item);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BizException("summary.queue.interrupted");
            }
        }

        awaitBatch(latch, error);
        return Map.copyOf(merged);
    }

    private void workerLoop() {
        while (running) {
            try {
                WorkItem item = queue.take();
                if (item.isPoison()) {
                    break;
                }
                processItem(item);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void processItem(WorkItem item) {
        try {
            rateLimiter.acquire();
            Map<Integer, String> batchResult = summaryAgent.unifyBatch(
                    item.batch(), item.styleGuide(), item.requirement(),
                    item.sourceLang(), item.targetLang(), item.modelCode());
            item.merged().putAll(batchResult);
            if (!ObjectUtils.isEmpty(item.onPrimarySentencesDone())) {
                item.onPrimarySentencesDone().accept(item.batch().primarySentenceCount());
            }
        } catch (Exception e) {
            item.error().compareAndSet(null, e);
            log.warn("Summary batch failed, primary={}-{}: {}",
                    item.batch().primaryStartOrderNo(), item.batch().primaryEndOrderNo(), e.getMessage());
        } finally {
            item.latch().countDown();
        }
    }

    private void awaitBatch(CountDownLatch latch, AtomicReference<Throwable> error) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException("summary.queue.interrupted");
        }
        Throwable err = error.get();
        if (!ObjectUtils.isEmpty(err)) {
            if (err instanceof RuntimeException re) {
                throw re;
            }
            throw new IllegalStateException(err);
        }
    }

    private record WorkItem(
            SummaryBatch batch,
            String styleGuide,
            String requirement,
            String sourceLang,
            String targetLang,
            String modelCode,
            Map<Integer, String> merged,
            CountDownLatch latch,
            IntConsumer onPrimarySentencesDone,
            AtomicReference<Throwable> error) {

        static final WorkItem POISON = new WorkItem(
                null, null, null, null, null, null, null, null, null, null);

        boolean isPoison() {
            return this == POISON;
        }
    }
}
