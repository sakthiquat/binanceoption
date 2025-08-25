package com.trading.bot.model;

import java.math.BigDecimal;

public class OrderModifyRequest {
    private String orderId;
    private String symbol;
    private BigDecimal quantity;
    private BigDecimal price;

    public OrderModifyRequest() {}

    public OrderModifyRequest(String orderId, String symbol, BigDecimal quantity, BigDecimal price) {
        this.orderId = orderId;
        this.symbol = symbol;
        this.quantity = quantity;
        this.price = price;
    }

    // Getters and Setters
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
}