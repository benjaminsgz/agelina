package com.yeven.thread.framework.pipeline.slot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SlotPatchTest {

    @Test
    void shouldStoreOneSlotPatch() {
        SlotPatch patch = SlotPatch.of(3, "value");

        assertEquals(1, patch.size());
        assertEquals(3, patch.slotIdAt(0));
        assertEquals("value", patch.valueAt(0));
    }

    @Test
    void shouldStoreTwoSlotPatch() {
        SlotPatch patch = SlotPatch.of(1, "a", 2, "b");

        assertEquals(2, patch.size());
        assertEquals(1, patch.slotIdAt(0));
        assertEquals("a", patch.valueAt(0));
        assertEquals(2, patch.slotIdAt(1));
        assertEquals("b", patch.valueAt(1));
    }

    @Test
    void shouldCopyArrayBackedPatch() {
        int[] slotIds = new int[]{1, 2, 3};
        Object[] values = new Object[]{"a", "b", "c"};
        SlotPatch patch = SlotPatch.from(slotIds, values);

        slotIds[2] = 9;
        values[2] = "changed";

        assertEquals(3, patch.size());
        assertEquals(3, patch.slotIdAt(2));
        assertEquals("c", patch.valueAt(2));
    }

    @Test
    void shouldRejectInvalidIndex() {
        SlotPatch patch = SlotPatch.of(1, "a");

        assertThrows(IndexOutOfBoundsException.class, () -> patch.slotIdAt(1));
    }
}
