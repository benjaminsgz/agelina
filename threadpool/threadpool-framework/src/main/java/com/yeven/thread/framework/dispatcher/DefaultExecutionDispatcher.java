package com.yeven.thread.framework.dispatcher;

import com.yeven.thread.framework.executor.ExecutorRegistry;
import com.yeven.thread.framework.constant.ExecutionMode;
import com.yeven.thread.framework.executor.NodeCompletion;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;

/**
 * 默认的任务分发器实现，底层由 {@link ExecutorRegistry} 提供线程池支持。
 * 
 * <p><b>设计必要性与核心价值：</b></p>
 * <ul>
 *   <li><b>多模式感知与路由：</b> 它扮演了物理执行引擎（线程池）与逻辑调度层之间的网关，可根据传入的 {@link ExecutionMode}，自适应将任务路由至合适的线程池（如专门负责大量阻塞操作的 IO 线程池、或者负责计算密集型任务的 CPU 线程池）。</li>
 *   <li><b>零堆内存开销调度（NodeDispatcher）：</b> 实现了 {@link NodeDispatcher} 接口，在执行图（DAG）节点级别的 Runnable 任务时，直接提交给底层 {@link Executor#execute(Runnable)} 运行。这种设计彻底避免了使用普通的 {@link CompletableFuture}，减少了高频执行复杂工作流时的内存瞬时开销。</li>
 *   <li><b>稳健的容错与安全边界：</b> 针对 DIRECT 执行模式提供无缝同步执行保障；同时针对拒绝执行异常（RejectedExecutionException）及其它运行时潜在异常进行了安全捕获，防止异常破坏线程池，并直接向 Future/NodeCompletion 回报失败状态。</li>
 * </ul>
 */
public class DefaultExecutionDispatcher implements ExecutionDispatcher, NodeDispatcher {

    private final ExecutorRegistry executorRegistry;

    /**
     * 创建默认分发器实例。
     *
     * @param executorRegistry 包含各执行模式对应线程池的注册表
     */
    public DefaultExecutionDispatcher(ExecutorRegistry executorRegistry) {
        this.executorRegistry = executorRegistry;
    }

    @Override
    public <T> CompletableFuture<T> dispatch(ExecutionMode mode, Supplier<T> supplier) {
        try {
            // 如果是 DIRECT 模式，直接在当前线程同步调用并返回已完成的 Future
            if (mode == ExecutionMode.DIRECT) {
                return CompletableFuture.completedFuture(supplier.get());
            }

            // 获取对应的线程池进行异步执行
            Executor executor = executorRegistry.getExecutor(mode);
            return CompletableFuture.supplyAsync(supplier, executor);
        } catch (RejectedExecutionException e) {
            // 线程池队列满拒绝策略异常处理
            return CompletableFuture.failedFuture(e);
        } catch (Throwable t) {
            // 捕获其他任何异常并返回异常结束 the Future
            return CompletableFuture.failedFuture(t);
        }
    }

    @Override
    public void dispatchNode(ExecutionMode mode, Runnable task, NodeCompletion completion) {
        try {
            // [DIRECT 模式同步通道]：若执行模式指定为直接同步执行（DIRECT），
            // 则不提交到任何线程池，直接在当前调用线程同步运行该节点逻辑，避免线程上下文切换开销。
            if (mode == ExecutionMode.DIRECT) {
                runNode(task, completion);
                return;
            }

            // [异步线程路由与投递]：根据执行模式从注册表中检索出物理关联的 Executor 线程池，
            // 随后在不产生任何 Future 包装对象的情况下，通过原生的 execute 方法将包装 Runnable 投递到线程池队列中异步执行。
            Executor executor = executorRegistry.getExecutor(mode);
            executor.execute(() -> runNode(task, completion));
        } catch (RejectedExecutionException e) {
            // [熔断/溢出保护机制]：当底层线程池队列饱和并触发拒绝执行策略（RejectedExecutionException）时，
            // 捕获此异常并直接通过 NodeCompletion 汇报给 DAG 引擎，以便触发 Fail-Fast 或超时补偿逻辑。
            completion.complete(e);
        } catch (Throwable t) {
            // [系统级异常捕获]：拦截在提交阶段（例如获取线程池或生成闭包时）可能发生的其他任何非预期 Throwable 异常，
            // 并将其原样回传至回调接口以保障调度引擎生命周期的完整性。
            completion.complete(t);
        }
    }

    /**
     * 运行具体的节点任务逻辑，并触发完成状态回调。
     *
     * @param task 节点的可运行任务
     * @param completion 回调接口
     */
    private static void runNode(Runnable task, NodeCompletion completion) {
        try {
            // [核心执行路径]：执行具体的节点业务逻辑或流水线阶段
            task.run();
            // [执行完成反馈]：无异常顺利执行结束，传入 null 标志着节点生命周期成功结束
            completion.complete(null);
        } catch (Throwable error) {
            // [异常穿透与防护]：捕获执行过程中产生的所有错误或异常，
            // 确保错误能够安全传递回回调接口，从而触发表的异常处理，避免线程无声消亡。
            completion.complete(error);
        }
    }
}
