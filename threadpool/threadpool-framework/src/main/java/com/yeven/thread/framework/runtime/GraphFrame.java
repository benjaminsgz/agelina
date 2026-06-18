package com.yeven.thread.framework.runtime;

import com.yeven.thread.framework.table.SlotSymbolTable;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 运行帧，保存单次图执行的并发控制状态和临时数据存储区。
 * 
 * <p><b>设计必要性与核心价值：</b></p>
 * <ul>
 *   <li><b>并发执行流隔离：</b> 每次调用 execute 方法都会产生一个全新的 GraphFrame 实例，确保多次并发执行之间的插槽数据和依赖计数器互不干扰。</li>
 * </ul>
 */
public final class GraphFrame<C> {

    public final SlotState slotState;
    public final RuntimeSlotView<C> view;
    public final AtomicReference<C> terminalResult = new AtomicReference<>();
    public final int[] remainingDependencies;
    public final CompletableFuture<C> result = new CompletableFuture<>();

    public GraphFrame(
            C initialContext,
            SlotSymbolTable symbolTable,
            int slotCount,
            int[] initialRemainingDependencies) {
        this.slotState = new SlotState(slotCount);
        this.view = new RuntimeSlotView<>(initialContext, slotState, symbolTable);
        this.remainingDependencies = Arrays.copyOf(
                initialRemainingDependencies,
                initialRemainingDependencies.length);
    }
}
