package com.yeven.thread.dag.demo.quote.service;

import com.yeven.thread.dag.demo.quote.context.QuoteContext;
import com.yeven.thread.dag.demo.quote.dto.QuoteRequest;
import com.yeven.thread.dag.demo.quote.dto.QuoteResponse;
import com.yeven.thread.dag.demo.quote.flow.QuoteFlowFactory;
import com.yeven.thread.framework.pipeline.SlotAsyncGraph;
import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.springframework.stereotype.Service;

@Service
public class QuoteService {

    private final Function<QuoteRequest, CompletableFuture<QuoteResponse>> previewQuoteProcess;

    public QuoteService(QuoteFlowFactory quoteFlowFactory) {
        SlotAsyncGraph<QuoteContext> graph = quoteFlowFactory.createQuoteGraph();
        this.previewQuoteProcess = request -> {
            QuoteContext initContext = QuoteContext.init(
                    request.getUserId(),
                    request.getSku(),
                    request.getQuantity(),
                    request.getCouponCode()
            );
            return graph.execute(initContext).thenApply(this::toResponse);
        };
    }

    public Function<QuoteRequest, CompletableFuture<QuoteResponse>> previewQuote() {
        return previewQuoteProcess;
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
