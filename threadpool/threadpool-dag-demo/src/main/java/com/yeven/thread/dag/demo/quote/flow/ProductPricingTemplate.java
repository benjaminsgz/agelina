package com.yeven.thread.dag.demo.quote.flow;

import com.yeven.thread.dag.demo.quote.context.QuoteContext;
import com.yeven.thread.dag.demo.quote.gateway.ProductCatalogGateway;
import com.yeven.thread.dag.demo.quote.model.ProductInfo;
import com.yeven.thread.framework.executor.ExecutionMode;
import com.yeven.thread.framework.pipeline.AsyncGraphTemplate;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * Reusable subgraph for loading product data and calculating the base amount.
 */
@Component
public class ProductPricingTemplate implements AsyncGraphTemplate<QuoteContext> {

    private final ProductCatalogGateway productCatalogGateway;

    public ProductPricingTemplate(ProductCatalogGateway productCatalogGateway) {
        this.productCatalogGateway = productCatalogGateway;
    }

    @Override
    public void apply(com.yeven.thread.framework.pipeline.AsyncGraphTemplateContext<QuoteContext> context) {
        context.addStep("loadProduct", "input", ExecutionMode.IO, quoteContext -> {
            ProductInfo product = productCatalogGateway.getBySku(quoteContext.getSku());
            return quoteContext.withProduct(product.getProductName(), product.getUnitPrice());
        });

        context.addStep("calculateBaseAmount", "loadProduct", ExecutionMode.CPU, quoteContext ->
                quoteContext.withBaseAmount(
                        quoteContext.getUnitPrice().multiply(BigDecimal.valueOf(quoteContext.getQuantity()))
                )
        );
    }
}
