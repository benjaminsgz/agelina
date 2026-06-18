package com.yeven.thread.framework.executor;

/**
 * 拓扑图节点执行分发完成的回调接口。
 * 
 * <p><b>设计必要性与核心价值：</b></p>
 * <ul>
 *   <li><b>轻量化事件回调机制：</b> 在没有 CompletableFuture 包装的高性能零分配分发模式（{@link com.yeven.thread.framework.dispatcher.NodeDispatcher}）下，此接口作为事件通知的载体，用于在任务完成或抛出异常时回调通知拓扑调度引擎。</li>
 *   <li><b>单向生命周期驱动：</b> 仅包含单个带有 Throwable 参数的完结方法，最小化接口语义设计，便于通过 Lambda 表达式和匿名内部类轻量高效地进行状态流转。</li>
 * </ul>
 */
@FunctionalInterface
public interface NodeCompletion {

    /**
     * 汇报节点任务执行的完成状态。
     *
     * @param error 执行成功时为 null；若发生异常导致节点运行终止，则为具体的异常对象
     */
    void complete(Throwable error);
}
