package com.yeven.thread.dag.demo.quote.flow;

import com.yeven.thread.dag.demo.quote.context.QuoteContext;
import com.yeven.thread.framework.executor.ExecutionMode;
import com.yeven.thread.framework.pipeline.AsyncGraphTemplate;
import com.yeven.thread.framework.pipeline.AsyncGraphTemplateContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

/**
 * Reusable subgraph for discount calculations.
 */
@Component
public class DiscountTemplate implements AsyncGraphTemplate<QuoteContext> {

    @Override
    public void apply(AsyncGraphTemplateContext<QuoteContext> context) {
        context.addJoinStep(
                "calculateMemberDiscount",
                java.util.List.of("baseAmount", "memberProfile"),
                ExecutionMode.CPU,
                QuoteContext::mergeAll,
                quoteContext -> quoteContext.withMemberDiscount(
                        percentage(quoteContext.getBaseAmount(), memberDiscountRate(quoteContext.getMemberLevel()))
                )
        );

        context.addStep("calculateCouponDiscount", "baseAmount", ExecutionMode.CPU, quoteContext ->
                quoteContext.withCouponDiscount(
                        percentage(quoteContext.getBaseAmount(), couponDiscountRate(quoteContext.getCouponCode()))
                )
        );
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
}
