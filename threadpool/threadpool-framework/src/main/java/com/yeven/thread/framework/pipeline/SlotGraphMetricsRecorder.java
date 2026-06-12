package com.yeven.thread.framework.pipeline;

import com.yeven.thread.framework.executor.ExecutionMode;

/**
 * 低开销的拓扑图节点执行指标监控钩子接口，服务于 {@link SlotAsyncGraph}。
 *
 * <p>核心框架模块故意不强依赖任何具体的监控后端。在生产环境中，可以通过适配此接口将数据接入
 * Micrometer、日志、JFR 事件或自定义的内存指标记录器。</p>
 */
@FunctionalInterface
public interface SlotGraphMetricsRecorder {

    /**
     * 记录单次节点执行的耗时和结果指标。
     *
     * @param nodeName 图中声明的节点名称
     * @param mode 节点执行使用的路由模式
     * @param role 节点角色（例如 "PATCH" 或 "TERMINAL"）
     * @param queueWaitNanos 队列等待时长纳秒数，即从依赖就绪触发调度到节点实际开始执行的时间；若调度前失败则为 0
     * @param runNanos 节点逻辑及槽写入的执行耗时纳秒数
     * @param success 节点是否执行成功
     * @param error 异常对象，执行成功时为 {@code null}
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
     * 返回一个无操作（No-op）的指标记录器实例，避免任何性能损耗。
     *
     * @return 默认的空操作指标记录器
     */
    static SlotGraphMetricsRecorder noop() {
        return NoopSlotGraphMetricsRecorder.INSTANCE;
    }
}

/**
 * 默认的空操作指标记录器实现。
 */
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
        // 无操作
    }
}
