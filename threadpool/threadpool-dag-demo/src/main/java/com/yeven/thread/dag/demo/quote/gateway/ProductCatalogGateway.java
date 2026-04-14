package com.yeven.thread.dag.demo.quote.gateway;

import com.yeven.thread.dag.demo.common.exception.BizException;
import com.yeven.thread.dag.demo.quote.model.ProductInfo;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ProductCatalogGateway {

    private static final Map<String, ProductInfo> PRODUCTS = Map.of(
            "SKU-1001", new ProductInfo("SKU-1001", "Mechanical Keyboard", new BigDecimal("699.00")),
            "SKU-1002", new ProductInfo("SKU-1002", "Noise Cancelling Headset", new BigDecimal("899.00")),
            "SKU-1003", new ProductInfo("SKU-1003", "4K Monitor", new BigDecimal("2199.00"))
    );

    public ProductInfo getBySku(String sku) {
        sleep(120);
        ProductInfo product = PRODUCTS.get(sku);
        if (product == null) {
            throw new BizException("Unknown sku: " + sku);
        }
        return product;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while loading product", e);
        }
    }
}
