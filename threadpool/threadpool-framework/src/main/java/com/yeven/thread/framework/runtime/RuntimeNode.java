package com.yeven.thread.framework.runtime;

import com.yeven.thread.framework.constant.ExecutionMode;
import com.yeven.thread.framework.constant.SlotNodeRole;
import com.yeven.thread.framework.pipeline.ReadOnlySlotContextView;
import com.yeven.thread.framework.pipeline.SlotPatch;
import com.yeven.thread.framework.definition.SlotAsyncGraphNodeDefinition;
import java.util.Arrays;
import java.util.Map;

import java.util.function.Function;

/**
 * 运行期节点记录，存储已编译好的节点拓扑及求值函数快照。
 * 
 * <p>
 * <b>设计必要性与核心价值：</b>
 * </p>
 * <ul>
 * <li><b>消除运行时反射与查找：</b> 缓存了节点所有的只读属性与预编译的槽索引，在热路径执行时直接通过数组索引读写，实现极佳的执行效率。</li>
 * </ul>
 */
public record RuntimeNode<C>(
        String name,
        int[] dependencyIndexes,
        ExecutionMode mode,
        int[] readSlots,
        int[] declaredWriteSlotsSorted,
        int singleWriteSlot,
        SlotNodeRole role,
        Function<ReadOnlySlotContextView<C>, Object> slotEvaluator,
        Function<ReadOnlySlotContextView<C>, SlotPatch> patchEvaluator,
        Function<ReadOnlySlotContextView<C>, C> terminalEvaluator) {

    public static <C> RuntimeNode<C> from(
            SlotAsyncGraphNodeDefinition<C> definition,
            Map<String, Integer> nodeIndexByName) {
        int[] sortedWriteSlots = definition.getDeclaredWriteSlots();
        Arrays.sort(sortedWriteSlots);
        int[] dependencyIndexes = new int[definition.getDependencies().size()];
        for (int i = 0; i < dependencyIndexes.length; i++) {
            String dependency = definition.getDependencies().get(i);
            Integer dependencyIndex = nodeIndexByName.get(dependency);
            if (dependencyIndex == null) {
                throw new IllegalStateException(
                        "Node '" + definition.getName() + "' references unknown dependency '" + dependency + "'");
            }
            dependencyIndexes[i] = dependencyIndex;
        }
        int singleWriteSlot = definition.getSlotEvaluator() == null ? -1 : sortedWriteSlots[0];
        return new RuntimeNode<>(
                definition.getName(),
                dependencyIndexes,
                definition.getMode(),
                definition.getReadSlots(),
                sortedWriteSlots,
                singleWriteSlot,
                definition.getRole(),
                definition.getSlotEvaluator(),
                definition.getPatchEvaluator(),
                definition.getTerminalEvaluator());
    }
}
