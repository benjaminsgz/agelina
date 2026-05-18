package com.yeven.thread.framework.pipeline;

import com.yeven.thread.framework.executor.ExecutionMode;

/**
 * Low-overhead metrics hook for {@link SlotAsyncGraph} node execution.
 *
 * <p>The framework module does not depend on a metrics backend. Production code can adapt this
 * callback to Micrometer, logs, JFR events, or a custom in-memory recorder.</p>
 */
@FunctionalInterface
public interface SlotGraphMetricsRecorder {

    /**
     * Records one node execution attempt.
     *
     * @param nodeName node name declared in the graph
     * @param mode execution mode used for dispatch
     * @param role node role, currently {@code PATCH} or {@code TERMINAL}
     * @param queueWaitNanos time from dependency completion to supplier start; zero if dispatch failed before start
     * @param runNanos time spent inside node evaluator and slot publication
     * @param success whether the node completed successfully
     * @param error failure, or {@code null} on success
     */
    void recordNode(
            String nodeName,
            ExecutionMode mode,
            String role,
            long queueWaitNanos,
            long runNanos,
            boolean success,
            Throwable error
    );

    /**
     * Returns a recorder that performs no work.
     */
    static SlotGraphMetricsRecorder noop() {
        return NoopSlotGraphMetricsRecorder.INSTANCE;
    }
}

final class NoopSlotGraphMetricsRecorder implements SlotGraphMetricsRecorder {

    static final NoopSlotGraphMetricsRecorder INSTANCE = new NoopSlotGraphMetricsRecorder();

    private NoopSlotGraphMetricsRecorder() {
    }

    @Override
    public void recordNode(
            String nodeName,
            ExecutionMode mode,
            String role,
            long queueWaitNanos,
            long runNanos,
            boolean success,
            Throwable error
    ) {
        // no-op
    }
}
