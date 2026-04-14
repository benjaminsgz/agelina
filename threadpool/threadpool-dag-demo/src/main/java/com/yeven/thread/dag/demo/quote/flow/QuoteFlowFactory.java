package com.yeven.thread.dag.demo.quote.flow;

import com.yeven.thread.dag.demo.common.exception.BizException;
import com.yeven.thread.dag.demo.quote.context.QuoteContext;
import com.yeven.thread.dag.demo.quote.gateway.InventoryGateway;
import com.yeven.thread.dag.demo.quote.gateway.MemberProfileGateway;
import com.yeven.thread.dag.demo.quote.model.MemberProfile;
import com.yeven.thread.framework.decorator.CompositeStepDecorator;
import com.yeven.thread.framework.executor.ExecutionMode;
import com.yeven.thread.framework.pipeline.AsyncGraph;
import com.yeven.thread.framework.pipeline.AsyncGraphBuilder;
import com.yeven.thread.framework.pipeline.AsyncStepFactory;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class QuoteFlowFactory {

    private final AsyncStepFactory stepFactory;
    private final CompositeStepDecorator decorator;
    private final InventoryGateway inventoryGateway;
    private final MemberProfileGateway memberProfileGateway;
    private final ProductPricingTemplate productPricingTemplate;
    private final DiscountTemplate discountTemplate;

    public QuoteFlowFactory(
            AsyncStepFactory stepFactory,
            CompositeStepDecorator decorator,
            InventoryGateway inventoryGateway,
            MemberProfileGateway memberProfileGateway,
            ProductPricingTemplate productPricingTemplate,
            DiscountTemplate discountTemplate
    ) {
        this.stepFactory = stepFactory;
        this.decorator = decorator;
        this.inventoryGateway = inventoryGateway;
        this.memberProfileGateway = memberProfileGateway;
        this.productPricingTemplate = productPricingTemplate;
        this.discountTemplate = discountTemplate;
    }

    public AsyncGraph<QuoteContext> createQuoteGraph() {
        AsyncGraphBuilder<QuoteContext> builder = new AsyncGraphBuilder<QuoteContext>(stepFactory, decorator)
                .addRootStep("validateRequest", ExecutionMode.DIRECT, (QuoteContext context) -> {
                    QuoteContext normalized = context.normalized();
                    if (normalized.getQuantity() <= 0) {
                        throw new BizException("quantity must be positive");
                    }
                    return normalized;
                })
                .addStep("loadInventory", "validateRequest", ExecutionMode.IO, (QuoteContext context) ->
                        context.withInventory(inventoryGateway.getAvailableStock(context.getSku()))
                )
                .addStep("loadMemberProfile", "validateRequest", ExecutionMode.IO, (QuoteContext context) -> {
                    MemberProfile memberProfile = memberProfileGateway.getByUserId(context.getUserId());
                    return context.withMemberLevel(memberProfile.getLevel());
                });

        var pricing = builder.addTemplate(
                "pricing",
                productPricingTemplate,
                Map.of("input", "validateRequest")
        );

        var discounts = builder.addTemplate(
                "discounts",
                discountTemplate,
                Map.of(
                        "baseAmount", pricing.ref("calculateBaseAmount"),
                        "memberProfile", "loadMemberProfile"
                )
        );

        return builder
                .addJoinStep(
                        "buildQuote",
                        List.of(
                                pricing.ref("calculateBaseAmount"),
                                discounts.ref("calculateMemberDiscount"),
                                discounts.ref("calculateCouponDiscount"),
                                "loadInventory"
                        ),
                        ExecutionMode.DIRECT,
                        QuoteContext::mergeAll,
                        (QuoteContext context) -> {
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

    private static BigDecimal defaultMoney(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }
}
