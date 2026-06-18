package com.yeven.thread.framework.hook;

import com.yeven.thread.framework.constant.ExecutionMode;

/**
 * 低开销的拓扑图节点执行指标监控钩子接口，服务于 {@link com.yeven.thread.framework.graph.SlotAsyncGraph}。
 * 
 * <p><b>设计必要性与核心价值：</b></p>
 * <ul>
 *   <li><b>零性能损耗与可插拔设计：</b> 为了使高频执行的异步图框架不强绑定于任何外部监控基础设施，该接口作为轻量级桥梁引入。默认通过 {@link NoopSlotGraphMetricsRecorder} 实现无损空操作（No-op），确保不需要指标收集的用户无需支付任何时间戳获取与对象开销。</li>
 *   <li><b>细粒度生产级度量：</b> 暴露排队等待耗时、运行期执行耗时、节点角色、节点模式等完整纳秒级上下文，使生产系统能够自适应扩展对接 Prometheus (Micrometer), JFR (Java Flight Recorder) 或 OpenTelemetry 进行实时全链路拓扑大盘绘制。</li>
 * </ul>
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
