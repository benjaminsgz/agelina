package com.yeven.thread.framework.executor;

/**
 * 低内存开销的拓扑图节点任务分发器。
 *
 * <p>与 {@link ExecutionDispatcher} 不同，该接口在分发节点任务时不会为每个节点创建新的
 * {@code CompletableFuture} 对象。它适用于已经持有最终结果 Future 和依赖计数器的编译后有向无环图（DAG）运行期。</p>
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
