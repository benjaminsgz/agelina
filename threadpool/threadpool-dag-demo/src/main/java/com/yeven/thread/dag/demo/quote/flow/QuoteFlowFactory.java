package com.yeven.thread.dag.demo.quote.flow;

import com.yeven.thread.dag.demo.common.exception.BizException;
import com.yeven.thread.dag.demo.quote.context.QuoteContext;
import com.yeven.thread.dag.demo.quote.gateway.InventoryGateway;
import com.yeven.thread.dag.demo.quote.gateway.MemberProfileGateway;
import com.yeven.thread.dag.demo.quote.gateway.ProductCatalogGateway;
import com.yeven.thread.dag.demo.quote.model.MemberProfile;
import com.yeven.thread.dag.demo.quote.model.ProductInfo;
import com.yeven.thread.framework.decorator.CompositeStepDecorator;
import com.yeven.thread.framework.executor.ExecutionMode;
import com.yeven.thread.framework.pipeline.AsyncGraph;
import com.yeven.thread.framework.pipeline.AsyncGraphBuilder;
import com.yeven.thread.framework.pipeline.AsyncStepFactory;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class QuoteFlowFactory {

    private final AsyncStepFactory stepFactory;
    private final CompositeStepDecorator decorator;
    private final ProductCatalogGateway productCatalogGateway;
    private final InventoryGateway inventoryGateway;
    private final MemberProfileGateway memberProfileGateway;

    public QuoteFlowFactory(
            AsyncStepFactory stepFactory,
            CompositeStepDecorator decorator,
            ProductCatalogGateway productCatalogGateway,
            InventoryGateway inventoryGateway,
            MemberProfileGateway memberProfileGateway
    ) {
        this.stepFactory = stepFactory;
        this.decorator = decorator;
        this.productCatalogGateway = productCatalogGateway;
        this.inventoryGateway = inventoryGateway;
        this.memberProfileGateway = memberProfileGateway;
    }

    public AsyncGraph<QuoteContext> createQuoteGraph() {
        return new AsyncGraphBuilder<QuoteContext>(stepFactory, decorator)
                .addRootStep("validateRequest", ExecutionMode.DIRECT, context -> {
                    QuoteContext normalized = context.normalized();
                    if (normalized.getQuantity() <= 0) {
                        throw new BizException("quantity must be positive");
                    }
                    return normalized;
                })
                .addStep("loadProduct", "validateRequest", ExecutionMode.IO, context -> {
                    ProductInfo product = productCatalogGateway.getBySku(context.getSku());
                    return context.withProduct(product.getProductName(), product.getUnitPrice());
                })
                .addStep("loadInventory", "validateRequest", ExecutionMode.IO, context ->
                        context.withInventory(inventoryGateway.getAvailableStock(context.getSku()))
                )
                .addStep("loadMemberProfile", "validateRequest", ExecutionMode.IO, context -> {
                    MemberProfile memberProfile = memberProfileGateway.getByUserId(context.getUserId());
                    return context.withMemberLevel(memberProfile.getLevel());
                })
                .addStep("calculateBaseAmount", "loadProduct", ExecutionMode.CPU, context ->
                        context.withBaseAmount(context.getUnitPrice().multiply(BigDecimal.valueOf(context.getQuantity())))
                )
                .addJoinStep(
                        "calculateMemberDiscount",
                        List.of("calculateBaseAmount", "loadMemberProfile"),
                        ExecutionMode.CPU,
                        QuoteContext::mergeAll,
                        context -> context.withMemberDiscount(
                                percentage(context.getBaseAmount(), memberDiscountRate(context.getMemberLevel()))
                        )
                )
                .addStep("calculateCouponDiscount", "calculateBaseAmount", ExecutionMode.CPU, context ->
                        context.withCouponDiscount(
                                percentage(context.getBaseAmount(), couponDiscountRate(context.getCouponCode()))
                        )
                )
                .addJoinStep(
                        "buildQuote",
                        List.of("calculateBaseAmount", "calculateMemberDiscount", "calculateCouponDiscount", "loadInventory"),
                        ExecutionMode.DIRECT,
                        QuoteContext::mergeAll,
                        context -> {
                            BigDecimal memberDiscount = defaultMoney(context.getMemberDiscount());
                            BigDecimal couponDiscount = defaultMoney(context.getCouponDiscount());
                            BigDecimal payableAmount = context.getBaseAmount()
                                    .subtract(memberDiscount)
                                    .subtract(couponDiscount)
                                    .max(BigDecimal.ZERO)
                                    .setScale(2, RoundingMode.HALF_UP);
                            boolean stockSufficient = context.getAvailableStock() != null
                                    && context.getAvailableStock() >= context.getQuantity();
                            return context.withFinalQuote(payableAmount, stockSufficient);
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
