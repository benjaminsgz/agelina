package com.yeven.thread.framework.runtime;

import com.yeven.thread.framework.table.SlotSymbolTable;
import org.junit.jupiter.api.Test;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RuntimeSlotView 单元测试类。
 * 包含对已就绪/未就绪槽位读取、槽越界校验、以及 slotAs 强类型转换的测试。
 * 遵循 AAA (Arrange, Act, Assert) 模式。
 */
class RuntimeSlotViewTest {

    @Test
    void testReadReadySlot() {
        // Arrange (准备数据/对象)
        String mockContext = "test-context";
        SlotSymbolTable table = SlotSymbolTable.named(3, Map.of(
                0, "userId",
                1, "userAge"
        ));
        SlotState state = new SlotState(3);
        state.writeSlot(0, "user_123");
        state.writeSlot(1, 28);

        RuntimeSlotView<String> view = new RuntimeSlotView<>(mockContext, state, table);

        // Act (执行被测方法)
        String context = view.context();
        boolean hasUser = view.hasSlot(0);
        boolean hasAge = view.hasSlot("userAge");
        Object valUser = view.slot(0);
        Object valAge = view.slot("userAge");

        // Assert (断言预期结果)
        assertEquals("test-context", context, "上下文值应匹配");
        assertTrue(hasUser, "槽位 0 应该已就绪");
        assertTrue(hasAge, "槽位 'userAge' 应该已就绪");
        assertEquals("user_123", valUser, "槽位 0 获取的值应正确");
        assertEquals(28, valAge, "槽位 'userAge' 获取的值应正确");
    }

    @Test
    void testReadNotReadySlotThrows() {
        // Arrange
        SlotSymbolTable table = SlotSymbolTable.anonymous(2);
        SlotState state = new SlotState(2);
        RuntimeSlotView<Void> view = new RuntimeSlotView<>(null, state, table);

        // Act & Assert
        // 验证读取未写入就绪的槽位时抛出 IllegalStateException
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> view.slot(0));
        assertTrue(ex.getMessage().contains("Slot not ready"), "异常消息应包含 Slot not ready");
    }

    @Test
    void testReadOutOfBoundsSlotThrows() {
        // Arrange
        SlotSymbolTable table = SlotSymbolTable.anonymous(2);
        SlotState state = new SlotState(2);
        RuntimeSlotView<Void> view = new RuntimeSlotView<>(null, state, table);

        // Act & Assert
        // 1. 验证根据整型槽 ID 越界时（负数）抛出 IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () -> view.slot(-1));
        assertThrows(IllegalArgumentException.class, () -> view.hasSlot(-1));

        // 2. 验证根据整型槽 ID 越界上限时抛出 IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () -> view.slot(2));
        assertThrows(IllegalArgumentException.class, () -> view.hasSlot(2));

        // 3. 验证未知字符串符号查找槽位时抛出 IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () -> view.slot("unknownSymbol"));
        assertThrows(IllegalArgumentException.class, () -> view.hasSlot("unknownSymbol"));
    }

    @Test
    void testSlotAsTypeCastingSuccess() {
        // Arrange
        SlotSymbolTable table = SlotSymbolTable.named(2, Map.of(
                0, "userId",
                1, "userAge"
        ));
        SlotState state = new SlotState(2);
        state.writeSlot(0, "user_123");
        state.writeSlot(1, 28);
        RuntimeSlotView<Void> view = new RuntimeSlotView<>(null, state, table);

        // Act
        String userId = view.slotAs(0, String.class);
        Integer userAge = view.slotAs("userAge", Integer.class);

        // Assert
        assertEquals("user_123", userId, "强转 String 结果应匹配");
        assertEquals(28, userAge, "强转 Integer 结果应匹配");
    }

    @Test
    void testSlotAsTypeMismatchThrows() {
        // Arrange
        SlotSymbolTable table = SlotSymbolTable.named(2, Map.of(
                0, "userId"
        ));
        SlotState state = new SlotState(2);
        state.writeSlot(0, "user_123"); // 实际为 String 类型
        RuntimeSlotView<Void> view = new RuntimeSlotView<>(null, state, table);

        // Act & Assert
        // 尝试强转为 Integer，期望抛出 IllegalStateException，并包含类型不匹配的具体提示信息
        IllegalStateException exInt = assertThrows(
                IllegalStateException.class,
                () -> view.slotAs(0, Integer.class)
        );
        assertTrue(exInt.getMessage().contains("value type mismatch"), "异常消息应包含类型不匹配说明");
        assertTrue(exInt.getMessage().contains("expected=java.lang.Integer"), "异常消息应指明期望类型为 Integer");
        assertTrue(exInt.getMessage().contains("actual=java.lang.String"), "异常消息应指明实际类型为 String");

        // 尝试通过字符串符号强转为 Double，期望抛出 IllegalStateException
        IllegalStateException exSymbol = assertThrows(
                IllegalStateException.class,
                () -> view.slotAs("userId", Double.class)
        );
        assertTrue(exSymbol.getMessage().contains("value type mismatch"));
    }
}
