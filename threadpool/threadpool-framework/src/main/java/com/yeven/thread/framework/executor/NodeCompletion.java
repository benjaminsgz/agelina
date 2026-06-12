package com.yeven.thread.framework.executor;

/**
 * 拓扑图节点执行分发完成的回调接口。
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
