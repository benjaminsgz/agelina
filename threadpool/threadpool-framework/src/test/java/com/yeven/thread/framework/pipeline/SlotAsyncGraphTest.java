package com.yeven.thread.framework.pipeline;

import com.yeven.thread.framework.executor.DefaultExecutionDispatcher;
import com.yeven.thread.framework.executor.ExecutionMode;
import com.yeven.thread.framework.executor.ExecutorRegistry;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlotAsyncGraphTest {

    private static final int SLOT_STOCK = 0;
    private static final int SLOT_MEMBER_LEVEL = 1;
    private static final int SLOT_MEMBER_DISCOUNT = 2;
    private static final int SLOT_COUPON_DISCOUNT = 3;

    private final ExecutorService ioExecutor = Executors.newFixedThreadPool(2);
    private final ExecutorService cpuExecutor = Executors.newFixedThreadPool(2);

    @AfterEach
    void tearDown() {
        ioExecutor.shutdownNow();
        cpuExecutor.shutdownNow();
    }

    @Test
    void shouldExecuteSlotGraphWithArrayIndexedSlots() {
        SlotSymbolTable symbolTable = SlotSymbolTable.named(
                8,
                Map.of(
                        SLOT_STOCK, "availableStock",
                        SLOT_MEMBER_LEVEL, "memberLevel",
                        SLOT_MEMBER_DISCOUNT, "memberDiscount",
                        SLOT_COUPON_DISCOUNT, "couponDiscount"
                )
        );

        SlotAsyncGraph<QuoteContext> graph = new SlotAsyncGraphBuilder<QuoteContext>(stepFactory(), symbolTable)
                .addSlotStep(
                        "loadInventory",
                        List.of(),
                        ExecutionMode.IO,
                        new int[0],
                        SLOT_STOCK,
                        view -> 100
                )
                .addSlotStep(
                        "loadMemberLevel",
                        List.of(),
                        ExecutionMode.IO,
                        new int[0],
                        SLOT_MEMBER_LEVEL,
                        view -> "GOLD"
                )
                .addPatchStep(
                        "calculateDiscounts",
                        List.of("loadMemberLevel"),
                        ExecutionMode.CPU,
                        new int[]{SLOT_MEMBER_LEVEL},
                        new int[]{SLOT_MEMBER_DISCOUNT, SLOT_COUPON_DISCOUNT},
                        view -> {
                            String level = view.slotAs(SLOT_MEMBER_LEVEL, String.class);
                            BigDecimal memberDiscount = "GOLD".equals(level)
                                    ? new BigDecimal("15.00")
                                    : BigDecimal.ZERO;
                            BigDecimal couponDiscount = "SAVE10".equals(view.context().couponCode())
                                    ? new BigDecimal("10.00")
                                    : BigDecimal.ZERO;
                            return SlotPatch.of(
                                    SLOT_MEMBER_DISCOUNT,
                                    memberDiscount,
                                    SLOT_COUPON_DISCOUNT,
                                    couponDiscount
                            );
                        }
                )
                .addTerminalStep(
                        "finalize",
                        List.of("loadInventory", "calculateDiscounts"),
                        ExecutionMode.DIRECT,
                        new int[]{SLOT_STOCK, SLOT_MEMBER_DISCOUNT, SLOT_COUPON_DISCOUNT},
                        view -> {
                            BigDecimal base = view.context().baseAmount();
                            BigDecimal payable = base
                                    .subtract(view.slotAs(SLOT_MEMBER_DISCOUNT, BigDecimal.class))
                                    .subtract(view.slotAs(SLOT_COUPON_DISCOUNT, BigDecimal.class));
                            boolean stockEnough = view.slotAs(SLOT_STOCK, Integer.class) >= view.context().quantity();
                            return view.context().withFinal(payable.max(BigDecimal.ZERO), stockEnough);
                        }
                )
                .build();

        QuoteContext result = graph.execute(new QuoteContext(3, "SAVE10", new BigDecimal("100.00"), null, false)).join();

        assertEquals(new BigDecimal("75.00"), result.payableAmount());
        assertTrue(result.stockEnough());
    }

    @Test
    void shouldRejectDuplicateSlotWriterAtBuildTime() {
        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                new SlotAsyncGraphBuilder<QuoteContext>(stepFactory(), 4)
                        .addSlotStep("a", List.of(), ExecutionMode.DIRECT, new int[0], 0, view -> 1)
                        .addSlotStep("b", List.of(), ExecutionMode.DIRECT, new int[0], 0, view -> 2)
                        .addTerminalStep("t", List.of("a", "b"), ExecutionMode.DIRECT, new int[]{0}, ReadOnlySlotContextView::context)
                        .build()
        );

        assertTrue(exception.getMessage().contains("Slot write collision"));
    }

    @Test
    void shouldRejectReadSlotWithoutDependencyPath() {
        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                new SlotAsyncGraphBuilder<QuoteContext>(stepFactory(), 4)
                        .addSlotStep("producer", List.of(), ExecutionMode.DIRECT, new int[0], 0, view -> "x")
                        .addSlotStep("consumer", List.of(), ExecutionMode.DIRECT, new int[]{0}, 1, view -> "y")
                        .addTerminalStep("terminal", List.of("producer", "consumer"), ExecutionMode.DIRECT, new int[]{1}, ReadOnlySlotContextView::context)
                        .build()
        );

        assertTrue(exception.getMessage().contains("without dependency path"));
    }

    @Test
    void shouldSupportSymbolicSlotsWithoutManualIndex() {
        SlotAsyncGraph<QuoteContext> graph = new SlotAsyncGraphBuilder<QuoteContext>(stepFactory())
                .addSlotStep(
                        "loadStock",
                        List.of(),
                        ExecutionMode.IO,
                        List.of(),
                        "stock",
                        view -> 9
                )
                .addSlotStep(
                        "loadMemberLevel",
                        List.of(),
                        ExecutionMode.IO,
                        List.of(),
                        "memberLevel",
                        view -> "SILVER"
                )
                .addSymbolicPatchStep(
                        "calc",
                        List.of("loadStock", "loadMemberLevel"),
                        ExecutionMode.CPU,
                        List.of("memberLevel"),
                        List.of("memberDiscount", "couponDiscount"),
                        view -> SymbolicSlotPatch.of(
                                "memberDiscount",
                                "SILVER".equals(view.slotAs("memberLevel", String.class))
                                        ? new BigDecimal("8.00")
                                        : BigDecimal.ZERO,
                                "couponDiscount",
                                new BigDecimal("3.00")
                        )
                )
                .addTerminalStep(
                        "terminal",
                        List.of("calc"),
                        ExecutionMode.DIRECT,
                        List.of("stock", "memberDiscount", "couponDiscount"),
                        view -> {
                            BigDecimal payable = view.context().baseAmount()
                                    .subtract(view.slotAs("memberDiscount", BigDecimal.class))
                                    .subtract(view.slotAs("couponDiscount", BigDecimal.class));
                            boolean stockEnough = view.slotAs("stock", Integer.class) >= view.context().quantity();
                            return view.context().withFinal(payable, stockEnough);
                        }
                )
                .build();

        QuoteContext result = graph.execute(new QuoteContext(2, "SAVE10", new BigDecimal("40.00"), null, false)).join();
        assertEquals(new BigDecimal("29.00"), result.payableAmount());
        assertTrue(result.stockEnough());
    }

    private AsyncStepFactory stepFactory() {
        return new AsyncStepFactory(new DefaultExecutionDispatcher(new ExecutorRegistry(Map.of(
                ExecutionMode.IO, ioExecutor,
                ExecutionMode.CPU, cpuExecutor
        ))));
    }

    private record QuoteContext(
            int quantity,
            String couponCode,
            BigDecimal baseAmount,
            BigDecimal payableAmount,
            boolean stockEnough
    ) {
        private QuoteContext withFinal(BigDecimal newPayableAmount, boolean newStockEnough) {
            return new QuoteContext(quantity, couponCode, baseAmount, newPayableAmount, newStockEnough);
        }
    }
}
