package com.trading.bot.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class OrderResponse {
    @JsonProperty("orderId")
    private String orderId;
    
    @JsonProperty("symbol")
    private String symbol;
    
    @JsonProperty("status")
    private OrderStatus status;
    
    @JsonProperty("executedQty")
    private BigDecimal filledQuantity;
    
    @JsonProperty("avgPrice")
    private BigDecimal avgPrice;
    
    @JsonProperty("time")
    private LocalDateTime timestamp;
    
    @JsonProperty("side")
    private OrderSide side;
    
    @JsonProperty("origQty")
    private BigDecimal originalQuantity;
    
    @JsonProperty("price")
    private BigDecimal price;

    public OrderResponse() {}

    // Getters and Setters
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }

    public BigDecimal getFilledQuantity() { return filledQuantity; }
    public void setFilledQuantity(BigDecimal filledQuantity) { this.filledQuantity = filledQuantity; }

    public BigDecimal getAvgPrice() { return avgPrice; }
    public void setAvgPrice(BigDecimal avgPrice) { this.avgPrice = avgPrice; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public OrderSide getSide() { return side; }
    public void setSide(OrderSide side) { this.side = side; }

    public BigDecimal getOriginalQuantity() { return originalQuantity; }
    public void setOriginalQuantity(BigDecimal originalQuantity) { this.originalQuantity = originalQuantity; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public boolean isFilled() {
        return status == OrderStatus.FILLED;
    }

    public boolean isPartiallyFilled() {
        return status == OrderStatus.PARTIALLY_FILLED;
    }
}