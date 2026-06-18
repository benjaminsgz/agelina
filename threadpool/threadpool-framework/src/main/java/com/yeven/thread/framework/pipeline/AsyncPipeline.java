package com.yeven.thread.framework.pipeline;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 不可变的顺序异步管道。
 * 
 * <p><b>设计必要性与核心价值：</b></p>
 * <ul>
 *   <li><b>声明式串行流水线：</b> 与处理复杂分支或并行调度的 DAG 有向图不同，该管道专用于处理简单的顺序串行流任务。只有当前一个步骤的异步 {@code Future} 成功完成之后，下一个步骤才会被触发运行，简化了单向流水线的组装。</li>
 *   <li><b>优雅的 Fail-Fast 短路机制：</b> 在管道执行过程中，一旦任何一个步骤抛出异常或返回失败，整条流水线将立刻熔断，剩余后续步骤自动跳过，从而防止局部错误在管道中扩散或引起次生脏数据问题。</li>
 * </ul>
 * 
 * <p><b>并发防护警告：</b> 上下文对象 {@code C} 在所有管道步骤之间是共享的。请确保上下文的属性更新是线程安全的，或者在将此管道集成到更大的并发有向图（DAG）时已妥善处理其并发可见性。</p>
 *
 * @param <C> 管道的上下文类型
 */
public class AsyncPipeline<C> {

    private final AsyncStep<C> composed;

    /**
     * 根据传入的异步步骤列表构建异步管道。
     *
     * @param steps 异步步骤列表
     */
    public AsyncPipeline(List<AsyncStep<C>> steps) {
        AsyncStep<C> chain = AsyncStep.identity();
        for (AsyncStep<C> step : steps) {
            chain = chain.then(step);
        }
        this.composed = chain;
    }

    /**
     * 执行该异步管道。
     *
     * <p>如果其中任意一个步骤执行失败，返回的 Future 会异常结束，并且后续剩余的所有步骤都会被跳过（Fail-Fast 机制）。</p>
     *
     * @param context 初始上下文对象
     * @return 包含最终执行完毕上下文的 {@link CompletableFuture}
     */
    public CompletableFuture<C> execute(C context) {
        return composed.apply(context);
    }
}
