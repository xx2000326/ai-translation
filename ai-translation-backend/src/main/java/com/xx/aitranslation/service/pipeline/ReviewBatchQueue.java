package com.xx.aitranslation.service.pipeline;

import com.xx.aitranslation.agent.ReviewAgent;
import com.xx.aitranslation.common.BizException;
import com.xx.aitranslation.dto.ReviewResult;
import com.xx.aitranslation.entity.TranslationSentence;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntConsumer;

/**
 * 审校批次全局有界队列：每批 {@link ReviewAgent#batchSize()} 句，多 Worker 并行调用审校 LLM。
 * <p>
 * 调度模式与 {@link SentenceTranslationQueue} 一致，共享 {@link TranslationRateLimiter} 与并发配置。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewBatchQueue {

    private final ReviewAgent reviewAgent;
    private final TranslationRateLimiter rateLimiter;

    @Value("${app.translation.concurrency:8}")
    private int concurrency;

    @Value("${app.translation.queue-capacity:16}")
    private int queueCapacity;

    private BlockingQueue<WorkItem> queue;
    private volatile boolean running = true;

    @PostConstruct
    void startWorkers() {
        int workers = concurrency <= 0 ? 1 : concurrency;
        int capacity = queueCapacity <= 0 ? workers * 2 : queueCapacity;
        queue = new ArrayBlockingQueue<>(capacity);
        for (int i = 0; i < workers; i++) {
            Thread.startVirtualThread(this::workerLoop);
        }
        log.info("Review batch queue started: workers={}, capacity={}", workers, capacity);
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
     * 并行审校全文：按固定批次拆分入队，阻塞等待全部批次完成并合并结果。
     */
    public ReviewResult executeReview(
            List<TranslationSentence> segs,
            String requirement,
            String sourceLang,
            String targetLang,
            String reviewModel,
            IntConsumer onSentenceScored) {
        if (ObjectUtils.isEmpty(segs)) {
            return new ReviewResult(List.of());
        }

        int batchSize = reviewAgent.batchSize();
        List<List<TranslationSentence>> batches = splitBatches(segs, batchSize);
        int batchCount = batches.size();

        CountDownLatch latch = new CountDownLatch(batchCount);
        AtomicInteger scoredSentences = new AtomicInteger(0);
        AtomicReference<Throwable> error = new AtomicReference<>();
        List<ReviewResult.SegmentReview> merged =
                Collections.synchronizedList(new ArrayList<>(segs.size()));

        for (List<TranslationSentence> batch : batches) {
            WorkItem item = new WorkItem(
                    batch, requirement, sourceLang, targetLang, reviewModel,
                    merged, latch, scoredSentences, onSentenceScored, error);
            try {
                queue.put(item);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BizException("review.queue.interrupted");
            }
        }

        awaitBatch(latch, error);
        return new ReviewResult(List.copyOf(merged));
    }

    private static List<List<TranslationSentence>> splitBatches(
            List<TranslationSentence> segs, int batchSize) {
        List<List<TranslationSentence>> batches = new ArrayList<>();
        for (int i = 0; i < segs.size(); i += batchSize) {
            batches.add(new ArrayList<>(segs.subList(i, Math.min(i + batchSize, segs.size()))));
        }
        return batches;
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
            List<ReviewResult.SegmentReview> batchResult = reviewAgent.reviewBatch(
                    item.batch(), item.requirement(), item.sourceLang(), item.targetLang(), item.reviewModel());
            item.merged().addAll(batchResult);
            if (!ObjectUtils.isEmpty(item.onSentenceScored())) {
                int scored = item.scoredSentences().addAndGet(item.batch().size());
                item.onSentenceScored().accept(scored);
            }
        } catch (Exception e) {
            item.error().compareAndSet(null, e);
            log.warn("Review batch failed, size={}: {}", item.batch().size(), e.getMessage());
        } finally {
            item.latch().countDown();
        }
    }

    private void awaitBatch(CountDownLatch latch, AtomicReference<Throwable> error) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException("review.queue.interrupted");
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
            List<TranslationSentence> batch,
            String requirement,
            String sourceLang,
            String targetLang,
            String reviewModel,
            List<ReviewResult.SegmentReview> merged,
            CountDownLatch latch,
            AtomicInteger scoredSentences,
            IntConsumer onSentenceScored,
            AtomicReference<Throwable> error) {

        static final WorkItem POISON = new WorkItem(
                null, null, null, null, null, null, null, null, null, null);

        boolean isPoison() {
            return this == POISON;
        }
    }
}
