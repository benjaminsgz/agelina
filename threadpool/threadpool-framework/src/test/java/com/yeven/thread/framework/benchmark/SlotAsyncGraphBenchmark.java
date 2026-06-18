package com.yeven.thread.framework.benchmark;

import com.yeven.thread.framework.constant.ExecutionMode;
import com.yeven.thread.framework.dispatcher.DefaultExecutionDispatcher;
import com.yeven.thread.framework.executor.ExecutorRegistry;
import com.yeven.thread.framework.factory.AsyncStepFactory;
import com.yeven.thread.framework.graph.SlotAsyncGraph;
import com.yeven.thread.framework.graph.SlotAsyncGraphBuilder;
import com.yeven.thread.framework.pipeline.ReadOnlySlotContextView;
import com.yeven.thread.framework.pipeline.SlotPatch;
import com.yeven.thread.framework.table.SlotSymbolTable;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Agelina 响应式调度引擎的 JMH 微基准测试。
 * 
 * <p><b>主要测试目的：</b></p>
 * <ul>
 *   <li><b>调度器吞吐量：</b> 评估在高并发多线程争用下，无锁就绪标记状态机与基于 VarHandle 的计数减一算法的调度性能。</li>
 *   <li><b>DIRECT 同步通道开销：</b> 测量完全跑在 DIRECT 模式下的极速 DAG 执行效率。</li>
 *   <li><b>多线程池分发开销：</b> 模拟真实 IO 线程池与 CPU 线程池切换时调度器的时延与排队消耗。</li>
 * </ul>
 */
@State(Scope.Benchmark)
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class SlotAsyncGraphBenchmark {

    // 静态定义插槽索引，模拟真实的业务参数传递
    private static final int SLOT_USER_ID = 0;
    private static final int SLOT_USER_NAME = 1;
    private static final int SLOT_USER_BALANCE = 2;
    private static final int SLOT_VIP_LEVEL = 3;

    private ExecutorService ioExecutor;
    private ExecutorService cpuExecutor;

    private SlotAsyncGraph<BenchmarkContext> directGraph;
    private SlotAsyncGraph<BenchmarkContext> threadPoolGraph;

    @Setup
    public void setup() {
        // [线程池初始化]：预先构建测试用 CPU 及 IO 线程池
        ioExecutor = Executors.newFixedThreadPool(4);
        cpuExecutor = Executors.newFixedThreadPool(4);

        // 构造符号插槽表映射
        SlotSymbolTable symbolTable = SlotSymbolTable.named(
                8,
                Map.of(
                        SLOT_USER_ID, "userId",
                        SLOT_USER_NAME, "userName",
                        SLOT_USER_BALANCE, "userBalance",
                        SLOT_VIP_LEVEL, "vipLevel"
                )
        );

        // 构造带线程池的执行分发器与步骤工厂
        ExecutorRegistry registry = new ExecutorRegistry(Map.of(
                ExecutionMode.IO, ioExecutor,
                ExecutionMode.CPU, cpuExecutor
        ));
        DefaultExecutionDispatcher dispatcher = new DefaultExecutionDispatcher(registry);
        AsyncStepFactory stepFactory = new AsyncStepFactory(dispatcher);

        // ==========================================
        // 1. 构建完全跑在 DIRECT 模式下的同步 DAG 执行图（钻石拓扑）
        // ==========================================
        directGraph = new SlotAsyncGraphBuilder<BenchmarkContext>(stepFactory, symbolTable)
                .addSlotStep(
                        "loadUser",
                        List.of(),
                        ExecutionMode.DIRECT,
                        new int[0],
                        SLOT_USER_ID,
                        view -> 10001L
                )
                .addSlotStep(
                        "loadName",
                        List.of("loadUser"),
                        ExecutionMode.DIRECT,
                        new int[]{SLOT_USER_ID},
                        SLOT_USER_NAME,
                        view -> "User_" + view.slot(SLOT_USER_ID)
                )
                .addPatchStep(
                        "loadProfile",
                        List.of("loadUser"),
                        ExecutionMode.DIRECT,
                        new int[]{SLOT_USER_ID},
                        new int[]{SLOT_USER_BALANCE, SLOT_VIP_LEVEL},
                        view -> SlotPatch.of(
                                SLOT_USER_BALANCE, new BigDecimal("888.88"),
                                SLOT_VIP_LEVEL, 3
                        )
                )
                .addTerminalStep(
                        "finalize",
                        List.of("loadName", "loadProfile"),
                        ExecutionMode.DIRECT,
                        new int[]{SLOT_USER_NAME, SLOT_USER_BALANCE, SLOT_VIP_LEVEL},
                        view -> view.context().withResult(
                                view.slotAs(SLOT_USER_NAME, String.class),
                                view.slotAs(SLOT_USER_BALANCE, BigDecimal.class),
                                view.slotAs(SLOT_VIP_LEVEL, Integer.class)
                        )
                )
                .build();

        // ==========================================
        // 2. 构建跑在真实 CPU/IO 异步线程池中的 DAG 执行图
        // ==========================================
        threadPoolGraph = new SlotAsyncGraphBuilder<BenchmarkContext>(stepFactory, symbolTable)
                .addSlotStep(
                        "loadUser",
                        List.of(),
                        ExecutionMode.IO, // 模拟 IO 动作
                        new int[0],
                        SLOT_USER_ID,
                        view -> 10002L
                )
                .addSlotStep(
                        "loadName",
                        List.of("loadUser"),
                        ExecutionMode.CPU, // 模拟 CPU 计算
                        new int[]{SLOT_USER_ID},
                        SLOT_USER_NAME,
                        view -> "User_Async_" + view.slot(SLOT_USER_ID)
                )
                .addPatchStep(
                        "loadProfile",
                        List.of("loadUser"),
                        ExecutionMode.IO, // 模拟 IO 动作
                        new int[]{SLOT_USER_ID},
                        new int[]{SLOT_USER_BALANCE, SLOT_VIP_LEVEL},
                        view -> SlotPatch.of(
                                SLOT_USER_BALANCE, new BigDecimal("999.99"),
                                SLOT_VIP_LEVEL, 5
                        )
                )
                .addTerminalStep(
                        "finalize",
                        List.of("loadName", "loadProfile"),
                        ExecutionMode.DIRECT, // 终点就地同步执行
                        new int[]{SLOT_USER_NAME, SLOT_USER_BALANCE, SLOT_VIP_LEVEL},
                        view -> view.context().withResult(
                                view.slotAs(SLOT_USER_NAME, String.class),
                                view.slotAs(SLOT_USER_BALANCE, BigDecimal.class),
                                view.slotAs(SLOT_VIP_LEVEL, Integer.class)
                        )
                )
                .build();
    }

    @TearDown
    public void tearDown() {
        // [优雅停机]：释放物理线程池资源
        ioExecutor.shutdownNow();
        cpuExecutor.shutdownNow();
    }

    /**
     * 测试场景一：完全工作在 DIRECT 模式下的同步无锁调度性能。
     * 用于压榨框架极速响应和零排队通道下的执行速度。
     */
    @Benchmark
    @Threads(1)
    public BenchmarkContext testDirectGraphSingleThread() throws Exception {
        BenchmarkContext ctx = new BenchmarkContext(123);
        return directGraph.execute(ctx).get();
    }

    /**
     * 测试场景二：多线程并发调用下，DIRECT 同步模式的吞吐极限。
     */
    @Benchmark
    @Threads(Threads.MAX)
    public BenchmarkContext testDirectGraphMultiThread() throws Exception {
        BenchmarkContext ctx = new BenchmarkContext(456);
        return directGraph.execute(ctx).get();
    }

    /**
     * 测试场景三：混合线程池异步调度性能（单调用线程测试）。
     * 用于评估在真实的线程上下文切换、Future 就绪通知以及 VarHandle 并发递减计数场景下的性能。
     */
    @Benchmark
    @Threads(1)
    public BenchmarkContext testThreadPoolGraphSingleThread() throws Exception {
        BenchmarkContext ctx = new BenchmarkContext(789);
        return threadPoolGraph.execute(ctx).get();
    }

    /**
     * 测试场景四：多线程并发调用下，混合线程池异步调度的吞吐性能极限。
     */
    @Benchmark
    @Threads(Threads.MAX)
    public BenchmarkContext testThreadPoolGraphMultiThread() throws Exception {
        BenchmarkContext ctx = new BenchmarkContext(999);
        return threadPoolGraph.execute(ctx).get();
    }

    /**
     * 基础基准测试上下文对象。
     */
    public static final class BenchmarkContext {
        private final long id;
        private final String name;
        private final BigDecimal balance;
        private final Integer vipLevel;

        public BenchmarkContext(long id) {
            this(id, null, null, null);
        }

        public BenchmarkContext(long id, String name, BigDecimal balance, Integer vipLevel) {
            this.id = id;
            this.name = name;
            this.balance = balance;
            this.vipLevel = vipLevel;
        }

        public BenchmarkContext withResult(String name, BigDecimal balance, Integer vipLevel) {
            return new BenchmarkContext(this.id, name, balance, vipLevel);
        }

        public String getName() {
            return name;
        }

        public BigDecimal getBalance() {
            return balance;
        }

        public Integer getVipLevel() {
            return vipLevel;
        }
    }

    /**
     * JMH 便捷启动主入口。开发人员可直接在 IDE 中运行该 main 函数来启动微基准测试。
     */
    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
                .include(SlotAsyncGraphBenchmark.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}
