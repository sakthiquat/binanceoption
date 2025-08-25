package com.trading.bot.model;

import java.math.BigDecimal;

public class OrderRequest {
    private String symbol;
    private OrderSide side;
    private BigDecimal quantity;
    private BigDecimal price;
    private OrderType type;

    public OrderRequest() {}

    public OrderRequest(String symbol, OrderSide side, BigDecimal quantity, BigDecimal price, OrderType type) {
        this.symbol = symbol;
        this.side = side;
        this.quantity = quantity;
        this.price = price;
        this.type = type;
    }

    // Getters and Setters
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public OrderSide getSide() { return side; }
    public void setSide(OrderSide side) { this.side = side; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public OrderType getType() { return type; }
    public void setType(OrderType type) { this.type = type; }
}