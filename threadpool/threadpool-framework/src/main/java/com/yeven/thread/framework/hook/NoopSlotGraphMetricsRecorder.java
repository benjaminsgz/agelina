package com.yeven.thread.framework.hook;

import com.yeven.thread.framework.constant.ExecutionMode;

/**
 * 默认的空操作指标记录器实现。
 * 
 * <p><b>设计必要性与核心价值：</b></p>
 * <ul>
 *   <li><b>零性能开销兜底：</b> 避免在高吞吐量场景下引入额外的记录负担。</li>
 * </ul>
 */
public final class NoopSlotGraphMetricsRecorder implements SlotGraphMetricsRecorder {

    public static final NoopSlotGraphMetricsRecorder INSTANCE = new NoopSlotGraphMetricsRecorder();

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
        // 无操作
    }
}
