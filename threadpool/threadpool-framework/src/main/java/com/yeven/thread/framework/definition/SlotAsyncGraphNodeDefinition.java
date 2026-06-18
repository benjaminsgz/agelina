package com.yeven.thread.framework.definition;

import com.yeven.thread.framework.constant.ExecutionMode;
import com.yeven.thread.framework.constant.SlotNodeRole;
import com.yeven.thread.framework.pipeline.ReadOnlySlotContextView;
import com.yeven.thread.framework.pipeline.SlotPatch;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * 基于插槽异步有向无环图（DAG）执行器的声明式节点定义类。
 * 
 * <p><b>设计必要性与核心价值：</b></p>
 * <ul>
 *   <li><b>元数据承载与规约：</b> 封装了单个 DAG 节点的核心配置（名称、执行模式、前置节点依赖、需要读取与声明写入的插槽）。它是构建期与编译期用来分析依赖闭包、写冲突和环路检测的唯一静态数据源。</li>
 *   <li><b>支持多种求值器（Evaluators）：</b> 支持单插槽值求值、多插槽 Patch 求值以及 Terminal 出口上下文求值，为不同类型的计算提供了灵活的多态静态工厂构建方法。</li>
 * </ul>
 *
 * @param <C> 基础上下文类型
 */
public final class SlotAsyncGraphNodeDefinition<C> {

    private final String name;
    private final ExecutionMode mode;
    private final List<String> dependencies;
    private final int[] readSlots;
    private final int[] declaredWriteSlots;
    private final SlotNodeRole role;
    private final Function<ReadOnlySlotContextView<C>, Object> slotEvaluator;
    private final Function<ReadOnlySlotContextView<C>, SlotPatch> patchEvaluator;
    private final Function<ReadOnlySlotContextView<C>, C> terminalEvaluator;

    private SlotAsyncGraphNodeDefinition(
            String name,
            ExecutionMode mode,
            List<String> dependencies,
            int[] readSlots,
            int[] declaredWriteSlots,
            SlotNodeRole role,
            Function<ReadOnlySlotContextView<C>, Object> slotEvaluator,
            Function<ReadOnlySlotContextView<C>, SlotPatch> patchEvaluator,
            Function<ReadOnlySlotContextView<C>, C> terminalEvaluator) {
        this.name = Objects.requireNonNull(name, "name");
        this.mode = Objects.requireNonNull(mode, "mode");
        this.dependencies = List.copyOf(Objects.requireNonNull(dependencies, "dependencies"));
        this.readSlots = Arrays.copyOf(Objects.requireNonNull(readSlots, "readSlots"), readSlots.length);
        this.declaredWriteSlots = Arrays.copyOf(
                Objects.requireNonNull(declaredWriteSlots, "declaredWriteSlots"),
                declaredWriteSlots.length);
        this.role = Objects.requireNonNull(role, "role");
        this.slotEvaluator = slotEvaluator;
        this.patchEvaluator = patchEvaluator;
        this.terminalEvaluator = terminalEvaluator;
    }

    /**
     * 静态工厂：创建一个只写入单个插槽的普通节点定义。
     */
    public static <C> SlotAsyncGraphNodeDefinition<C> slotNode(
            String name,
            ExecutionMode mode,
            List<String> dependencies,
            int[] readSlots,
            int writeSlot,
            Function<ReadOnlySlotContextView<C>, Object> evaluator) {
        return new SlotAsyncGraphNodeDefinition<>(
                name,
                mode,
                dependencies,
                readSlots,
                new int[] { writeSlot },
                SlotNodeRole.PATCH,
                Objects.requireNonNull(evaluator, "evaluator"),
                null,
                null);
    }

    /**
     * 静态工厂：创建一个声明写入多个插槽（修补写入）的节点定义。
     */
    public static <C> SlotAsyncGraphNodeDefinition<C> patchNode(
            String name,
            ExecutionMode mode,
            List<String> dependencies,
            int[] readSlots,
            int[] declaredWriteSlots,
            Function<ReadOnlySlotContextView<C>, SlotPatch> evaluator) {
        return new SlotAsyncGraphNodeDefinition<>(
                name,
                mode,
                dependencies,
                readSlots,
                declaredWriteSlots,
                SlotNodeRole.PATCH,
                null,
                Objects.requireNonNull(evaluator, "evaluator"),
                null);
    }

    /**
     * 静态工厂：创建一个作为图终结点的终端节点定义，负责解析和输出最终上下文。
     */
    public static <C> SlotAsyncGraphNodeDefinition<C> terminalNode(
            String name,
            ExecutionMode mode,
            List<String> dependencies,
            int[] readSlots,
            Function<ReadOnlySlotContextView<C>, C> evaluator) {
        return new SlotAsyncGraphNodeDefinition<>(
                name,
                mode,
                dependencies,
                readSlots,
                new int[0],
                SlotNodeRole.TERMINAL,
                null,
                null,
                Objects.requireNonNull(evaluator, "evaluator"));
    }

    public String getName() {
        return name;
    }

    public ExecutionMode getMode() {
        return mode;
    }

    public List<String> getDependencies() {
        return dependencies;
    }

    public int[] getReadSlots() {
        return Arrays.copyOf(readSlots, readSlots.length);
    }

    public int[] getDeclaredWriteSlots() {
        return Arrays.copyOf(declaredWriteSlots, declaredWriteSlots.length);
    }

    public SlotNodeRole getRole() {
        return role;
    }

    public Function<ReadOnlySlotContextView<C>, Object> getSlotEvaluator() {
        return slotEvaluator;
    }

    public Function<ReadOnlySlotContextView<C>, SlotPatch> getPatchEvaluator() {
        return patchEvaluator;
    }

    public Function<ReadOnlySlotContextView<C>, C> getTerminalEvaluator() {
        return terminalEvaluator;
    }
}
