package com.yeven.thread.dag.demo.quote.service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuotePreviewLimiterTest {

    @Test
    void shouldRejectWhenConcurrencyLimitIsReached() {
        QuotePreviewLimiter limiter = limiter(1);
        CompletableFuture<String> first = new CompletableFuture<>();

        CompletableFuture<String> accepted = limiter.execute(() -> first);
        CompletableFuture<String> rejected = limiter.execute(() -> CompletableFuture.completedFuture("second"));

        assertEquals(0, limiter.availablePermits());
        CompletionException exception = assertThrows(CompletionException.class, rejected::join);
        assertInstanceOf(RejectedExecutionException.class, exception.getCause());

        first.complete("first");
        assertEquals("first", accepted.join());
        assertEquals(1, limiter.availablePermits());
    }

    @Test
    void shouldReleasePermitWhenProcessFailsSynchronously() {
        QuotePreviewLimiter limiter = limiter(1);

        CompletableFuture<String> failed = limiter.execute(() -> {
            throw new IllegalStateException("boom");
        });

        CompletionException exception = assertThrows(CompletionException.class, failed::join);
        assertInstanceOf(IllegalStateException.class, exception.getCause());
        assertEquals(1, limiter.availablePermits());
    }

    @Test
    void shouldDisableLimitWhenMaxConcurrentIsNotPositive() {
        QuotePreviewLimiter limiter = limiter(0);

        CompletableFuture<String> first = limiter.execute(() -> CompletableFuture.completedFuture("first"));
        CompletableFuture<String> second = limiter.execute(() -> CompletableFuture.completedFuture("second"));

        assertEquals("first", first.join());
        assertEquals("second", second.join());
        assertEquals(Integer.MAX_VALUE, limiter.availablePermits());
    }

    private QuotePreviewLimiter limiter(int maxConcurrent) {
        QuotePreviewProperties properties = new QuotePreviewProperties();
        properties.setMaxConcurrent(maxConcurrent);
        return new QuotePreviewLimiter(properties);
    }
}
