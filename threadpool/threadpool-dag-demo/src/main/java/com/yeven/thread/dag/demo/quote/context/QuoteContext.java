package com.yeven.thread.dag.demo.quote.context;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class QuoteContext {

    private final String quoteId;
    private final String userId;
    private final String sku;
    private final int quantity;
    private final String couponCode;
    private final String productName;
    private final BigDecimal unitPrice;
    private final Integer availableStock;
    private final String memberLevel;
    private final BigDecimal baseAmount;
    private final BigDecimal memberDiscount;
    private final BigDecimal couponDiscount;
    private final BigDecimal payableAmount;
    private final Boolean stockSufficient;

    private QuoteContext(
            String quoteId,
            String userId,
            String sku,
            int quantity,
            String couponCode,
            String productName,
            BigDecimal unitPrice,
            Integer availableStock,
            String memberLevel,
            BigDecimal baseAmount,
            BigDecimal memberDiscount,
            BigDecimal couponDiscount,
            BigDecimal payableAmount,
            Boolean stockSufficient
    ) {
        this.quoteId = quoteId;
        this.userId = userId;
        this.sku = sku;
        this.quantity = quantity;
        this.couponCode = couponCode;
        this.productName = productName;
        this.unitPrice = unitPrice;
        this.availableStock = availableStock;
        this.memberLevel = memberLevel;
        this.baseAmount = baseAmount;
        this.memberDiscount = memberDiscount;
        this.couponDiscount = couponDiscount;
        this.payableAmount = payableAmount;
        this.stockSufficient = stockSufficient;
    }

    public static QuoteContext init(String userId, String sku, int quantity, String couponCode) {
        return new QuoteContext(
                UUID.randomUUID().toString(),
                userId,
                sku,
                quantity,
                couponCode,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    public QuoteContext normalized() {
        String normalizedCoupon = couponCode == null || couponCode.isBlank()
                ? null
                : couponCode.trim().toUpperCase();
        return copy(
                quoteId,
                userId.trim(),
                sku.trim().toUpperCase(),
                quantity,
                normalizedCoupon,
                productName,
                unitPrice,
                availableStock,
                memberLevel,
                baseAmount,
                memberDiscount,
                couponDiscount,
                payableAmount,
                stockSufficient
        );
    }

    public QuoteContext withProduct(String newProductName, BigDecimal newUnitPrice) {
        return copy(
                quoteId, userId, sku, quantity, couponCode, newProductName, newUnitPrice,
                availableStock, memberLevel, baseAmount, memberDiscount, couponDiscount,
                payableAmount, stockSufficient
        );
    }

    public QuoteContext withInventory(int newAvailableStock) {
        return copy(
                quoteId, userId, sku, quantity, couponCode, productName, unitPrice,
                newAvailableStock, memberLevel, baseAmount, memberDiscount, couponDiscount,
                payableAmount, stockSufficient
        );
    }

    public QuoteContext withMemberLevel(String newMemberLevel) {
        return copy(
                quoteId, userId, sku, quantity, couponCode, productName, unitPrice,
                availableStock, newMemberLevel, baseAmount, memberDiscount, couponDiscount,
                payableAmount, stockSufficient
        );
    }

    public QuoteContext withBaseAmount(BigDecimal newBaseAmount) {
        return copy(
                quoteId, userId, sku, quantity, couponCode, productName, unitPrice,
                availableStock, memberLevel, newBaseAmount, memberDiscount, couponDiscount,
                payableAmount, stockSufficient
        );
    }

    public QuoteContext withMemberDiscount(BigDecimal newMemberDiscount) {
        return copy(
                quoteId, userId, sku, quantity, couponCode, productName, unitPrice,
                availableStock, memberLevel, baseAmount, newMemberDiscount, couponDiscount,
                payableAmount, stockSufficient
        );
    }

    public QuoteContext withCouponDiscount(BigDecimal newCouponDiscount) {
        return copy(
                quoteId, userId, sku, quantity, couponCode, productName, unitPrice,
                availableStock, memberLevel, baseAmount, memberDiscount, newCouponDiscount,
                payableAmount, stockSufficient
        );
    }

    public QuoteContext withFinalQuote(BigDecimal newPayableAmount, boolean newStockSufficient) {
        return copy(
                quoteId, userId, sku, quantity, couponCode, productName, unitPrice,
                availableStock, memberLevel, baseAmount, memberDiscount, couponDiscount,
                newPayableAmount, newStockSufficient
        );
    }

    public QuoteContext merge(QuoteContext other) {
        Objects.requireNonNull(other, "other");
        return copy(
                quoteId != null ? quoteId : other.quoteId,
                userId != null ? userId : other.userId,
                sku != null ? sku : other.sku,
                quantity != 0 ? quantity : other.quantity,
                couponCode != null ? couponCode : other.couponCode,
                productName != null ? productName : other.productName,
                unitPrice != null ? unitPrice : other.unitPrice,
                availableStock != null ? availableStock : other.availableStock,
                memberLevel != null ? memberLevel : other.memberLevel,
                baseAmount != null ? baseAmount : other.baseAmount,
                memberDiscount != null ? memberDiscount : other.memberDiscount,
                couponDiscount != null ? couponDiscount : other.couponDiscount,
                payableAmount != null ? payableAmount : other.payableAmount,
                stockSufficient != null ? stockSufficient : other.stockSufficient
        );
    }

    public static QuoteContext mergeAll(List<QuoteContext> contexts) {
        if (contexts.isEmpty()) {
            throw new IllegalArgumentException("contexts must not be empty");
        }
        QuoteContext merged = contexts.get(0);
        for (int i = 1; i < contexts.size(); i++) {
            merged = merged.merge(contexts.get(i));
        }
        return merged;
    }

    private QuoteContext copy(
            String newQuoteId,
            String newUserId,
            String newSku,
            int newQuantity,
            String newCouponCode,
            String newProductName,
            BigDecimal newUnitPrice,
            Integer newAvailableStock,
            String newMemberLevel,
            BigDecimal newBaseAmount,
            BigDecimal newMemberDiscount,
            BigDecimal newCouponDiscount,
            BigDecimal newPayableAmount,
            Boolean newStockSufficient
    ) {
        return new QuoteContext(
                newQuoteId,
                newUserId,
                newSku,
                newQuantity,
                newCouponCode,
                newProductName,
                newUnitPrice,
                newAvailableStock,
                newMemberLevel,
                newBaseAmount,
                newMemberDiscount,
                newCouponDiscount,
                newPayableAmount,
                newStockSufficient
        );
    }

    public String getQuoteId() { return quoteId; }
    public String getUserId() { return userId; }
    public String getSku() { return sku; }
    public int getQuantity() { return quantity; }
    public String getCouponCode() { return couponCode; }
    public String getProductName() { return productName; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public Integer getAvailableStock() { return availableStock; }
    public String getMemberLevel() { return memberLevel; }
    public BigDecimal getBaseAmount() { return baseAmount; }
    public BigDecimal getMemberDiscount() { return memberDiscount; }
    public BigDecimal getCouponDiscount() { return couponDiscount; }
    public BigDecimal getPayableAmount() { return payableAmount; }
    public Boolean getStockSufficient() { return stockSufficient; }
}
