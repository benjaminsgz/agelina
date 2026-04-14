package com.yeven.thread.dag.demo.quote.dto;

import java.math.BigDecimal;

public class QuoteResponse {

    private final String quoteId;
    private final String userId;
    private final String sku;
    private final String productName;
    private final int quantity;
    private final String memberLevel;
    private final int availableStock;
    private final boolean stockSufficient;
    private final BigDecimal unitPrice;
    private final BigDecimal baseAmount;
    private final BigDecimal memberDiscount;
    private final BigDecimal couponDiscount;
    private final BigDecimal payableAmount;

    public QuoteResponse(
            String quoteId,
            String userId,
            String sku,
            String productName,
            int quantity,
            String memberLevel,
            int availableStock,
            boolean stockSufficient,
            BigDecimal unitPrice,
            BigDecimal baseAmount,
            BigDecimal memberDiscount,
            BigDecimal couponDiscount,
            BigDecimal payableAmount
    ) {
        this.quoteId = quoteId;
        this.userId = userId;
        this.sku = sku;
        this.productName = productName;
        this.quantity = quantity;
        this.memberLevel = memberLevel;
        this.availableStock = availableStock;
        this.stockSufficient = stockSufficient;
        this.unitPrice = unitPrice;
        this.baseAmount = baseAmount;
        this.memberDiscount = memberDiscount;
        this.couponDiscount = couponDiscount;
        this.payableAmount = payableAmount;
    }

    public String getQuoteId() { return quoteId; }
    public String getUserId() { return userId; }
    public String getSku() { return sku; }
    public String getProductName() { return productName; }
    public int getQuantity() { return quantity; }
    public String getMemberLevel() { return memberLevel; }
    public int getAvailableStock() { return availableStock; }
    public boolean isStockSufficient() { return stockSufficient; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getBaseAmount() { return baseAmount; }
    public BigDecimal getMemberDiscount() { return memberDiscount; }
    public BigDecimal getCouponDiscount() { return couponDiscount; }
    public BigDecimal getPayableAmount() { return payableAmount; }
}
