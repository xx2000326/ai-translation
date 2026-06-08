package com.xx.aitranslation.service.pipeline;

import com.xx.aitranslation.common.BizException;
import com.xx.aitranslation.entity.TranslationSentence;
import com.xx.aitranslation.enums.ProgressPhase;
import com.xx.aitranslation.service.TranslationTaskService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

/**
 * 句子级翻译全局有界队列：生产者 {@code put} 入队，固定数量虚拟线程 Worker {@code take} 消费。
 * <p>
 * 替代「为每句创建 CompletableFuture 并一次性提交线程池」模式，提供背压与跨任务 FIFO 调度。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SentenceTranslationQueue {

    private final TranslationTaskService translationTaskService;
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
        log.info("Sentence translation queue started: workers={}, capacity={}", workers, capacity);
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
     * 提交一批句子翻译任务并阻塞等待全部完成。
     *
     * @param taskId        任务 ID
     * @param sentences     待翻译句子列表
     * @param translator    翻译动作（由 Pipeline 注入 translateOne）
     * @param advice        审校建议，初翻传 null
     * @param progressPhase 进度阶段，{@code null} 表示不更新进度
     */
    public void executeBatch(
            Long taskId,
            List<TranslationSentence> sentences,
            BiConsumer<TranslationSentence, String> translator,
            String advice,
            ProgressPhase progressPhase) {
        executeBatch(taskId, sentences, translator, advice, progressPhase, true);
    }

    public void executeBatch(
            Long taskId,
            List<TranslationSentence> sentences,
            BiConsumer<TranslationSentence, String> translator,
            String advice,
            ProgressPhase progressPhase,
            boolean initProgress) {
        if (ObjectUtils.isEmpty(sentences)) {
            return;
        }
        int total = sentences.size();
        if (!ObjectUtils.isEmpty(progressPhase) && initProgress) {
            if (progressPhase == ProgressPhase.REVIEW) {
                translationTaskService.initReviewProgress(taskId, total);
            } else {
                translationTaskService.initTranslateProgress(taskId, total);
            }
        }

        CountDownLatch latch = new CountDownLatch(total);
        AtomicInteger completed = new AtomicInteger(0);
        AtomicReference<Throwable> error = new AtomicReference<>();

        for (TranslationSentence sentence : sentences) {
            WorkItem item = new WorkItem(
                    taskId, sentence, advice, translator, latch, completed, total, progressPhase, error);
            try {
                queue.put(item);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BizException("translation.queue.interrupted");
            }
        }

        awaitBatch(latch, error);
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
            item.translator().accept(item.sentence(), item.advice());
        } catch (Exception e) {
            item.error().compareAndSet(null, e);
            log.warn("Sentence translation failed, taskId={}, orderNo={}: {}",
                    item.taskId(), item.sentence().getOrderNo(), e.getMessage());
        } finally {
            if (!ObjectUtils.isEmpty(item.progressPhase())) {
                int done = item.completed().incrementAndGet();
                if (item.progressPhase() == ProgressPhase.REVIEW) {
                    translationTaskService.updateReviewProgress(item.taskId(), done, item.total());
                } else {
                    translationTaskService.updateTranslateProgress(item.taskId(), done, item.total());
                }
            }
            item.latch().countDown();
        }
    }

    private void awaitBatch(CountDownLatch latch, AtomicReference<Throwable> error) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException("translation.queue.interrupted");
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
            Long taskId,
            TranslationSentence sentence,
            String advice,
            BiConsumer<TranslationSentence, String> translator,
            CountDownLatch latch,
            AtomicInteger completed,
            int total,
            ProgressPhase progressPhase,
            AtomicReference<Throwable> error) {

        static final WorkItem POISON = new WorkItem(
                null, null, null, null, null, null, 0, null, null);

        boolean isPoison() {
            return this == POISON;
        }
    }
}
