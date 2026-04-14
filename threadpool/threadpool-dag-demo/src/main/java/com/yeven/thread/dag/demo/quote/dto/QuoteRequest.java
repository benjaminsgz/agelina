package com.yeven.thread.dag.demo.quote.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public class QuoteRequest {

    @NotBlank(message = "userId is required")
    private String userId;

    @NotBlank(message = "sku is required")
    private String sku;

    @Min(value = 1, message = "quantity must be at least 1")
    private int quantity;

    private String couponCode;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public String getCouponCode() {
        return couponCode;
    }

    public void setCouponCode(String couponCode) {
        this.couponCode = couponCode;
    }
}
