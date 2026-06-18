package com.yeven.thread.framework.table;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SlotSymbolTable 单元测试类。
 * 遵循 AAA (Arrange, Act, Assert) 模式。
 */
class SlotSymbolTableTest {

    @Test
    void testAnonymousCreation() {
        // Arrange (准备数据/对象)
        int slotCount = 5;

        // Act (执行被测方法)
        SlotSymbolTable table = SlotSymbolTable.anonymous(slotCount);

        // Assert (断言预期结果)
        // 校验匿名槽的数量是否正确，以及生成的默认符号名称是否为 slot[i] 格式
        assertEquals(5, table.slotCount(), "槽位数量应为 5");
        assertEquals("slot[0]", table.symbolOf(0), "第 0 个槽名称应为 slot[0]");
        assertEquals("slot[4]", table.symbolOf(4), "第 4 个槽名称应为 slot[4]");
        assertTrue(table.containsSymbol("slot[2]"), "应包含 slot[2] 符号");
        assertFalse(table.containsSymbol("slot[5]"), "不应包含越界的符号");
    }

    @Test
    void testAnonymousCreationInvalidCount() {
        // Arrange & Act & Assert
        // 验证传入负数或 0 时抛出 IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () -> SlotSymbolTable.anonymous(0), "槽位数量为 0 时应抛出异常");
        assertThrows(IllegalArgumentException.class, () -> SlotSymbolTable.anonymous(-1), "槽位数量为负数时应抛出异常");
    }

    @Test
    void testNamedCreation() {
        // Arrange
        int slotCount = 4;
        Map<Integer, String> symbolsBySlot = new HashMap<>();
        symbolsBySlot.put(0, "userId");
        symbolsBySlot.put(2, "userAge");

        // Act
        SlotSymbolTable table = SlotSymbolTable.named(slotCount, symbolsBySlot);

        // Assert
        // 校验命名符号表的尺寸，以及命名符号和匿名符号共存的情况
        assertEquals(4, table.slotCount());
        assertEquals("userId", table.symbolOf(0));
        assertEquals("slot[1]", table.symbolOf(1)); // 未命名的位置使用默认占位符
        assertEquals("userAge", table.symbolOf(2));
        assertEquals("slot[3]", table.symbolOf(3));

        // 校验根据符号查找槽 ID 映射
        assertEquals(0, table.slotIdOf("userId"));
        assertEquals(2, table.slotIdOf("userAge"));
        assertEquals(1, table.slotIdOf("slot[1]"));
    }

    @Test
    void testNamedCreationWithInvalidArguments() {
        // Arrange
        int slotCount = 3;

        // Act & Assert
        // 1. 验证传入空 Map 抛出 NullPointerException
        assertThrows(NullPointerException.class, () -> SlotSymbolTable.named(slotCount, null));

        // 2. 验证槽位 ID 越界上限抛出 IllegalArgumentException
        Map<Integer, String> mapWithOutBound = Map.of(3, "tooFar");
        assertThrows(IllegalArgumentException.class, () -> SlotSymbolTable.named(slotCount, mapWithOutBound));

        // 3. 验证槽位 ID 越界下限（负数）抛出 IllegalArgumentException
        Map<Integer, String> mapWithNegative = Map.of(-1, "negative");
        assertThrows(IllegalArgumentException.class, () -> SlotSymbolTable.named(slotCount, mapWithNegative));

        // 4. 验证符号为空或空白字符时抛出 IllegalArgumentException
        Map<Integer, String> mapWithEmpty = Map.of(1, "");
        assertThrows(IllegalArgumentException.class, () -> SlotSymbolTable.named(slotCount, mapWithEmpty));
        Map<Integer, String> mapWithBlank = Map.of(1, "   ");
        assertThrows(IllegalArgumentException.class, () -> SlotSymbolTable.named(slotCount, mapWithBlank));
    }

    @Test
    void testNamedDuplicateSymbol() {
        // Arrange
        int slotCount = 3;
        // 让两个不同的插槽映射到同一个符号名
        Map<Integer, String> mapWithDuplicate = Map.of(
                0, "userId",
                2, "userId"
        );

        // Act & Assert
        // 验证存在重复符号名称时，由于构建反向映射 map 会导致冲突，抛出 IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () -> SlotSymbolTable.named(slotCount, mapWithDuplicate));
    }

    @Test
    void testDynamicBuilder() {
        // Arrange
        SlotSymbolTable.Builder builder = SlotSymbolTable.builder();

        // Act
        // 动态分配两个符号
        int id1 = builder.getOrAllocate("userId");
        int id2 = builder.getOrAllocate("userToken");
        // 再次获取已存在的符号
        int id3 = builder.getOrAllocate("userId");

        // Assert
        assertEquals(0, id1, "首次分配应从 0 开始");
        assertEquals(1, id2, "第二次分配应为 1");
        assertEquals(0, id3, "再次分配同一符号应返回原有 ID");
        assertEquals(2, builder.size(), "已分配大小应为 2");

        // 构建符号表
        SlotSymbolTable table = builder.build();
        assertEquals(2, table.slotCount());
        assertEquals("userId", table.symbolOf(0));
        assertEquals("userToken", table.symbolOf(1));
    }

    @Test
    void testBuilderUnknownSymbol() {
        // Arrange
        SlotSymbolTable.Builder builder = SlotSymbolTable.builder();
        builder.getOrAllocate("userId");

        // Act & Assert
        // 验证获取未分配的未知符号 ID 时抛出 IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () -> builder.slotIdOf("unknownSymbol"));
        // 验证传入空白字符时抛出 IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () -> builder.getOrAllocate(""));
    }

    @Test
    void testBuilderEmptyBuild() {
        // Arrange
        SlotSymbolTable.Builder builder = SlotSymbolTable.builder();

        // Act & Assert
        // 验证在未分配任何槽位符号时调用 build() 抛出 IllegalStateException
        assertThrows(IllegalStateException.class, builder::build);
    }

    @Test
    void testSnapshotAndDescribe() {
        // Arrange
        SlotSymbolTable table = SlotSymbolTable.anonymous(3);

        // Act
        String[] snapshot = table.snapshot();
        String descNormal = table.describe(1);
        String descOutBound1 = table.describe(-1);
        String descOutBound2 = table.describe(3);

        // Assert
        assertEquals(3, snapshot.length);
        assertArrayEquals(new String[]{"slot[0]", "slot[1]", "slot[2]"}, snapshot);

        // 校验 snapshot 是只读隔离的，修改 snapshot 数组不应影响符号表本身
        snapshot[0] = "mutated";
        assertEquals("slot[0]", table.symbolOf(0), "符号表内部结构不应随快照的修改而改变");

        // 校验 describe 在正常与越界时的安全降级描述
        assertEquals("slot[1]", descNormal);
        assertEquals("slot[-1]", descOutBound1);
        assertEquals("slot[3]", descOutBound2);
    }
}
