package com.yeven.thread.framework.dispatcher;

import com.yeven.thread.framework.constant.ExecutionMode;
import com.yeven.thread.framework.executor.NodeCompletion;

/**
 * 低内存开销的拓扑图节点任务分发器接口。
 * 
 * <p><b>设计必要性与核心价值：</b></p>
 * <ul>
 *   <li><b>零堆内存分配（Zero-Allocation）优化：</b> 与基于 Future 返回值的 {@link ExecutionDispatcher} 不同，该接口在分发图节点任务时，不创建任何 {@code CompletableFuture} 对象。在高并发、深层拓扑的 DAG 大量被调度执行时，能消除数以万计的 Future 包装器分配，从而显著减轻 JVM GC 吞吐量压力。</li>
 *   <li><b>自适应引擎对接：</b> 为已持有最终结果 Future、依赖关系数组和原子计数器的静态编译 DAG 运行期提供最高效的底层硬件资源对接通路。</li>
 * </ul>
 */
public interface NodeDispatcher {

    /**
     * 分发并执行一个可运行的节点任务。
     *
     * @param mode 执行模式，决定任务路由到哪个线程池
     * @param task 节点的具体任务逻辑
     * @param completion 节点执行完成时的状态回调接口
     */
    void dispatchNode(ExecutionMode mode, Runnable task, NodeCompletion completion);
}
