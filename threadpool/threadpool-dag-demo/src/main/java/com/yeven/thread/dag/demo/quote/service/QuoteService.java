package com.yeven.thread.dag.demo.quote.service;

import com.yeven.thread.dag.demo.quote.context.QuoteContext;
import com.yeven.thread.dag.demo.quote.dto.QuoteRequest;
import com.yeven.thread.dag.demo.quote.dto.QuoteResponse;
import com.yeven.thread.dag.demo.quote.flow.QuoteFlowFactory;
import com.yeven.thread.framework.pipeline.AsyncGraph;
import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.springframework.stereotype.Service;

@Service
public class QuoteService {

    private final QuoteFlowFactory quoteFlowFactory;

    public QuoteService(QuoteFlowFactory quoteFlowFactory) {
        this.quoteFlowFactory = quoteFlowFactory;
    }

    public Function<QuoteRequest, CompletableFuture<QuoteResponse>> previewQuote() {
        return request -> {
            QuoteContext initContext = QuoteContext.init(
                    request.getUserId(),
                    request.getSku(),
                    request.getQuantity(),
                    request.getCouponCode()
            );
            AsyncGraph<QuoteContext> graph = quoteFlowFactory.createQuoteGraph();
            return graph.execute(initContext).thenApply(this::toResponse);
        };
    }

    private QuoteResponse toResponse(QuoteContext context) {
        return new QuoteResponse(
                context.getQuoteId(),
                context.getUserId(),
                context.getSku(),
                context.getProductName(),
                context.getQuantity(),
                context.getMemberLevel(),
                context.getAvailableStock() != null ? context.getAvailableStock() : 0,
                Boolean.TRUE.equals(context.getStockSufficient()),
                defaultMoney(context.getUnitPrice()),
                defaultMoney(context.getBaseAmount()),
                defaultMoney(context.getMemberDiscount()),
                defaultMoney(context.getCouponDiscount()),
                defaultMoney(context.getPayableAmount())
        );
    }

    private BigDecimal defaultMoney(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
