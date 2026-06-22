package com.yeven.thread.dag.demo.quote.flow;

import com.yeven.thread.dag.demo.common.exception.BizException;
import com.yeven.thread.dag.demo.quote.context.QuoteContext;
import com.yeven.thread.dag.demo.quote.gateway.InventoryGateway;
import com.yeven.thread.dag.demo.quote.gateway.MemberProfileGateway;
import com.yeven.thread.dag.demo.quote.gateway.ProductCatalogGateway;
import com.yeven.thread.dag.demo.quote.model.ProductInfo;
import com.yeven.thread.dag.demo.quote.model.MemberProfile;
import com.yeven.thread.framework.constant.ExecutionMode;
import com.yeven.thread.framework.pipeline.core.AsyncStepFactory;
import com.yeven.thread.framework.pipeline.graph.SlotAsyncGraph;
import com.yeven.thread.framework.pipeline.graph.SlotAsyncGraphBuilder;
import com.yeven.thread.framework.pipeline.slot.SymbolicSlotPatch;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class QuoteFlowFactory {

    private final AsyncStepFactory stepFactory;
    private final InventoryGateway inventoryGateway;
    private final MemberProfileGateway memberProfileGateway;
    private final ProductCatalogGateway productCatalogGateway;
    private final SlotAsyncGraph<QuoteContext> quoteGraph;

    public QuoteFlowFactory(
            AsyncStepFactory stepFactory,
            InventoryGateway inventoryGateway,
            MemberProfileGateway memberProfileGateway,
            ProductCatalogGateway productCatalogGateway
    ) {
        this.stepFactory = stepFactory;
        this.inventoryGateway = inventoryGateway;
        this.memberProfileGateway = memberProfileGateway;
        this.productCatalogGateway = productCatalogGateway;
        this.quoteGraph = buildQuoteGraph();
    }

    public SlotAsyncGraph<QuoteContext> createQuoteGraph() {
        return quoteGraph;
    }

    private SlotAsyncGraph<QuoteContext> buildQuoteGraph() {
        return new SlotAsyncGraphBuilder<QuoteContext>(stepFactory)
                .addSlotStep(
                        "validateRequest",
                        List.of(),
                        ExecutionMode.DIRECT,
                        List.of(),
                        "validatedContext",
                        view -> {
                            QuoteContext normalized = view.context().normalized();
                            if (normalized.getQuantity() <= 0) {
                                throw new BizException("quantity must be positive");
                            }
                            return normalized;
                        }
                )
                .addSlotStep(
                        "loadInventory",
                        List.of("validateRequest"),
                        ExecutionMode.IO,
                        List.of("validatedContext"),
                        "availableStock",
                        view -> {
                            QuoteContext normalized = view.slotAs("validatedContext", QuoteContext.class);
                            return inventoryGateway.getAvailableStock(normalized.getSku());
                        }
                )
                .addSlotStep(
                        "loadMemberProfile",
                        List.of("validateRequest"),
                        ExecutionMode.IO,
                        List.of("validatedContext"),
                        "memberLevel",
                        view -> {
                            QuoteContext normalized = view.slotAs("validatedContext", QuoteContext.class);
                            MemberProfile memberProfile = memberProfileGateway.getByUserId(normalized.getUserId());
                            return memberProfile.getLevel();
                        }
                )
                .addSymbolicPatchStep(
                        "loadProductAndBaseAmount",
                        List.of("validateRequest"),
                        ExecutionMode.IO,
                        List.of("validatedContext"),
                        List.of("productName", "unitPrice", "baseAmount"),
                        view -> {
                            QuoteContext normalized = view.slotAs("validatedContext", QuoteContext.class);
                            ProductInfo product = productCatalogGateway.getBySku(normalized.getSku());
                            BigDecimal baseAmount = product.getUnitPrice()
                                    .multiply(BigDecimal.valueOf(normalized.getQuantity()));
                            return SymbolicSlotPatch.from(
                                    new String[]{"productName", "unitPrice", "baseAmount"},
                                    new Object[]{product.getProductName(), product.getUnitPrice(), baseAmount}
                            );
                        }
                )
                .addSlotStep(
                        "calculateMemberDiscount",
                        List.of("loadProductAndBaseAmount", "loadMemberProfile"),
                        ExecutionMode.CPU,
                        List.of("baseAmount", "memberLevel"),
                        "memberDiscount",
                        view -> percentage(
                                view.slotAs("baseAmount", BigDecimal.class),
                                memberDiscountRate(view.slotAs("memberLevel", String.class))
                        )
                )
                .addSlotStep(
                        "calculateCouponDiscount",
                        List.of("loadProductAndBaseAmount", "validateRequest"),
                        ExecutionMode.CPU,
                        List.of("baseAmount", "validatedContext"),
                        "couponDiscount",
                        view -> {
                            QuoteContext normalized = view.slotAs("validatedContext", QuoteContext.class);
                            return percentage(
                                    view.slotAs("baseAmount", BigDecimal.class),
                                    couponDiscountRate(normalized.getCouponCode())
                            );
                        }
                )
                .addTerminalStep(
                        "buildQuote",
                        List.of(
                                "validateRequest",
                                "loadInventory",
                                "loadMemberProfile",
                                "loadProductAndBaseAmount",
                                "calculateMemberDiscount",
                                "calculateCouponDiscount"
                        ),
                        ExecutionMode.DIRECT,
                        List.of(
                                "validatedContext",
                                "availableStock",
                                "memberLevel",
                                "productName",
                                "unitPrice",
                                "baseAmount",
                                "memberDiscount",
                                "couponDiscount"
                        ),
                        view -> {
                            QuoteContext normalized = view.slotAs("validatedContext", QuoteContext.class);
                            Integer availableStock = view.slotAs("availableStock", Integer.class);
                            BigDecimal memberDiscount = defaultMoney(view.slotAs("memberDiscount", BigDecimal.class));
                            BigDecimal couponDiscount = defaultMoney(view.slotAs("couponDiscount", BigDecimal.class));
                            BigDecimal baseAmount = view.slotAs("baseAmount", BigDecimal.class);
                            BigDecimal payableAmount = baseAmount
                                    .subtract(memberDiscount)
                                    .subtract(couponDiscount)
                                    .max(BigDecimal.ZERO)
                                    .setScale(2, RoundingMode.HALF_UP);
                            boolean stockSufficient = availableStock >= normalized.getQuantity();
                            return normalized
                                    .withProduct(
                                            view.slotAs("productName", String.class),
                                            view.slotAs("unitPrice", BigDecimal.class)
                                    )
                                    .withInventory(availableStock)
                                    .withMemberLevel(view.slotAs("memberLevel", String.class))
                                    .withBaseAmount(baseAmount)
                                    .withMemberDiscount(memberDiscount)
                                    .withCouponDiscount(couponDiscount)
                                    .withFinalQuote(payableAmount, stockSufficient);
                        }
                )
                .build();
    }

    private static BigDecimal memberDiscountRate(String level) {
        return switch (level) {
            case "GOLD" -> new BigDecimal("0.15");
            case "SILVER" -> new BigDecimal("0.08");
            default -> BigDecimal.ZERO;
        };
    }

    private static BigDecimal couponDiscountRate(String couponCode) {
        if (couponCode == null || couponCode.isBlank()) {
            return BigDecimal.ZERO;
        }
        return switch (couponCode) {
            case "SAVE10" -> new BigDecimal("0.10");
            case "VIP20" -> new BigDecimal("0.20");
            default -> BigDecimal.ZERO;
        };
    }

    private static BigDecimal percentage(BigDecimal amount, BigDecimal rate) {
        return amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal defaultMoney(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }
}
