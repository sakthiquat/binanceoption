package com.trading.bot.exception;

/**
 * Exception thrown when order placement, modification, or cancellation fails.
 * May be recoverable depending on the specific error condition.
 */
public class OrderExecutionException extends TradingBotException {
    
    private final String orderId;
    private final String orderType;
    private final String symbol;
    
    public OrderExecutionException(String message) {
        super(message, "ORDER_ERROR", true);
        this.orderId = null;
        this.orderType = null;
        this.symbol = null;
    }
    
    public OrderExecutionException(String message, String orderId, String orderType, String symbol) {
        super(message, "ORDER_ERROR", true);
        this.orderId = orderId;
        this.orderType = orderType;
        this.symbol = symbol;
    }
    
    public OrderExecutionException(String message, Throwable cause, String orderId, String orderType, String symbol) {
        super(message, cause, "ORDER_ERROR", true);
        this.orderId = orderId;
        this.orderType = orderType;
        this.symbol = symbol;
    }
    
    public OrderExecutionException(String message, boolean recoverable) {
        super(message, "ORDER_ERROR", recoverable);
        this.orderId = null;
        this.orderType = null;
        this.symbol = null;
    }
    
    public String getOrderId() {
        return orderId;
    }
    
    public String getOrderType() {
        return orderType;
    }
    
    public String getSymbol() {
        return symbol;
    }
    
    @Override
    public String getFormattedMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.getFormattedMessage());
        
        if (orderId != null) {
            sb.append(" Order ID: ").append(orderId);
        }
        
        if (orderType != null) {
            sb.append(" Type: ").append(orderType);
        }
        
        if (symbol != null) {
            sb.append(" Symbol: ").append(symbol);
        }
        
        return sb.toString();
    }
    
    /**
     * Creates an order timeout exception
     */
    public static OrderExecutionException orderTimeout(String orderId, String symbol, int timeoutSeconds) {
        return new OrderExecutionException(
            String.format("Order %s for %s timed out after %d seconds", orderId, symbol, timeoutSeconds),
            orderId,
            "TIMEOUT",
            symbol
        );
    }
    
    /**
     * Creates an insufficient balance exception
     */
    public static OrderExecutionException insufficientBalance(String symbol, String balance) {
        return new OrderExecutionException(
            String.format("Insufficient balance for %s: %s", symbol, balance),
            "INSUFFICIENT_BALANCE",
            false
        );
    }
}