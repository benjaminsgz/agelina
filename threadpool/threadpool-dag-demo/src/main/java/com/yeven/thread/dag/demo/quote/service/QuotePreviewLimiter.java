package com.yeven.thread.dag.demo.quote.service;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class QuotePreviewLimiter {

    private final int maxConcurrent;
    private final Semaphore permits;

    public QuotePreviewLimiter(QuotePreviewProperties properties) {
        this.maxConcurrent = Objects.requireNonNull(properties, "properties").getMaxConcurrent();
        this.permits = maxConcurrent > 0 ? new Semaphore(maxConcurrent) : null;
    }

    public <T> CompletableFuture<T> execute(Supplier<CompletableFuture<T>> process) {
        Objects.requireNonNull(process, "process");
        if (permits == null) {
            return process.get();
        }
        if (!permits.tryAcquire()) {
            return CompletableFuture.failedFuture(new RejectedExecutionException(
                    "Quote preview concurrency limit exceeded: " + maxConcurrent
            ));
        }

        CompletableFuture<T> future;
        try {
            future = Objects.requireNonNull(process.get(), "process future must not be null");
        } catch (Throwable error) {
            permits.release();
            return CompletableFuture.failedFuture(error);
        }
        return future.whenComplete((result, error) -> permits.release());
    }

    int availablePermits() {
        return permits != null ? permits.availablePermits() : Integer.MAX_VALUE;
    }
}
