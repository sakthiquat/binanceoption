package com.trading.bot.model;

import java.math.BigDecimal;

public class OptionLeg {
    private String symbol;
    private OptionType type;
    private BigDecimal strike;
    private BigDecimal quantity;
    private BigDecimal entryPrice;
    private BigDecimal currentPrice;
    private OrderSide side;
    private String orderId;

    public OptionLeg() {}

    public OptionLeg(String symbol, OptionType type, BigDecimal strike, BigDecimal quantity, OrderSide side) {
        this.symbol = symbol;
        this.type = type;
        this.strike = strike;
        this.quantity = quantity;
        this.side = side;
    }

    // Getters and Setters
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public OptionType getType() { return type; }
    public void setType(OptionType type) { this.type = type; }

    public BigDecimal getStrike() { return strike; }
    public void setStrike(BigDecimal strike) { this.strike = strike; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

    public BigDecimal getEntryPrice() { return entryPrice; }
    public void setEntryPrice(BigDecimal entryPrice) { this.entryPrice = entryPrice; }

    public BigDecimal getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(BigDecimal currentPrice) { this.currentPrice = currentPrice; }

    public OrderSide getSide() { return side; }
    public void setSide(OrderSide side) { this.side = side; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public BigDecimal calculatePnL() {
        if (entryPrice == null || currentPrice == null) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal priceDiff = currentPrice.subtract(entryPrice);
        if (side == OrderSide.SELL) {
            priceDiff = priceDiff.negate();
        }
        
        return priceDiff.multiply(quantity);
    }
}