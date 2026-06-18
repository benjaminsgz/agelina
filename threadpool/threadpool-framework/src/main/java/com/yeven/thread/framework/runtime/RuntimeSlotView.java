package com.yeven.thread.framework.runtime;

import com.yeven.thread.framework.pipeline.ReadOnlySlotContextView;
import com.yeven.thread.framework.table.SlotSymbolTable;
import java.util.Objects;

/**
 * 运行期只读插槽视图的具体实现。
 * 
 * <p><b>设计必要性与核心价值：</b></p>
 * <ul>
 *   <li><b>安全性隔离：</b> 将底层的可写 SlotState 封装为只读的 View，暴露给 Handlers，确保执行期的数据只读安全性。</li>
 * </ul>
 */
public record RuntimeSlotView<C>(
        C context,
        SlotState slotState,
        SlotSymbolTable symbolTable) implements ReadOnlySlotContextView<C> {

    @Override
    public boolean hasSlot(int slotId) {
        validateSlotRange(slotId);
        return slotState.hasSlot(slotId);
    }

    @Override
    public boolean hasSlot(String slotSymbol) {
        Objects.requireNonNull(slotSymbol, "slotSymbol");
        return hasSlot(symbolTable.slotIdOf(slotSymbol));
    }

    @Override
    public Object slot(int slotId) {
        validateSlotRange(slotId);
        if (!slotState.hasSlot(slotId)) {
            throw new IllegalStateException("Slot not ready: " + symbolTable.describe(slotId));
        }
        return slotState.slot(slotId);
    }

    @Override
    public Object slot(String slotSymbol) {
        Objects.requireNonNull(slotSymbol, "slotSymbol");
        return slot(symbolTable.slotIdOf(slotSymbol));
    }

    private void validateSlotRange(int slotId) {
        if (slotId < 0 || slotId >= symbolTable.slotCount()) {
            throw new IllegalArgumentException(
                    "slot id out of range: " + slotId + ", slotCount=" + symbolTable.slotCount());
        }
    }
}
