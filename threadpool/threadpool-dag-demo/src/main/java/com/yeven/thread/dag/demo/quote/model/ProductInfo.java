package com.yeven.thread.dag.demo.quote.model;

import java.math.BigDecimal;

public class ProductInfo {

    private final String sku;
    private final String productName;
    private final BigDecimal unitPrice;

    public ProductInfo(String sku, String productName, BigDecimal unitPrice) {
        this.sku = sku;
        this.productName = productName;
        this.unitPrice = unitPrice;
    }

    public String getSku() { return sku; }
    public String getProductName() { return productName; }
    public BigDecimal getUnitPrice() { return unitPrice; }
}
