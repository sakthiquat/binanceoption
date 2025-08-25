package com.trading.bot.service;

import com.trading.bot.config.TradingConfig;
import com.trading.bot.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class AggressiveFillStrategy {
    
    private static final Logger logger = LoggerFactory.getLogger(AggressiveFillStrategy.class);
    
    @Autowired
    private TradingConfig config;
    
    @Autowired
    private OrderService orderService;
    
    @Autowired
    private BinanceOptionsClient binanceClient;
    
    @Autowired
    private NotificationService notificationService;
    
    public OrderResponse executeOrderWithAggressiveFill(OrderRequest orderRequest) throws Exception {
        logger.info("Executing aggressive fill for {} {} {} @ {}", 
                   orderRequest.getSide(), 
                   orderRequest.getQuantity(), 
                   orderRequest.getSymbol(), 
                   orderRequest.getPrice());
        
        // Place initial order
        OrderResponse orderResponse = orderService.placeOrder(orderRequest);
        
        if (orderResponse.isFilled()) {
            logger.info("Order filled immediately: {}", orderResponse.getOrderId());
            return orderResponse;
        }
        
        // Start aggressive fill monitoring
        return monitorAndUpdateOrder(orderResponse, orderRequest);
    }
    
    private OrderResponse monitorAndUpdateOrder(OrderResponse initialOrder, OrderRequest originalRequest) throws Exception {
        String orderId = initialOrder.getOrderId();
        String symbol = originalRequest.getSymbol();
        OrderSide side = originalRequest.getSide();
        BigDecimal quantity = originalRequest.getQuantity();
        
        LocalDateTime startTime = LocalDateTime.now();
        LocalDateTime timeoutTime = startTime.plusSeconds(config.getOrderTimeoutSeconds());
        
        logger.info("Starting aggressive fill monitoring for order {} (timeout: {} seconds)", 
                   orderId, config.getOrderTimeoutSeconds());
        
        while (LocalDateTime.now().isBefore(timeoutTime)) {
            try {
                // Wait for update interval
                Thread.sleep(config.getOrderUpdateIntervalSeconds() * 1000L);
                
                // Check current order status
                OrderResponse currentStatus = orderService.getOrderStatus(symbol, orderId);
                
                if (currentStatus.isFilled()) {
                    logger.info("Order {} filled successfully at average price {}", 
                               orderId, currentStatus.getAvgPrice());
                    return currentStatus;
                }
                
                if (currentStatus.isPartiallyFilled()) {
                    logger.info("Order {} partially filled: {}/{}", 
                               orderId, currentStatus.getFilledQuantity(), currentStatus.getOriginalQuantity());
                    // Continue monitoring for remaining quantity
                }
                
                // Get current market prices and update order with more aggressive pricing
                OrderBook orderBook = binanceClient.getOrderBook(symbol, 10);
                BigDecimal aggressivePrice = calculateAggressivePrice(side, orderBook);
                
                if (aggressivePrice != null && !aggressivePrice.equals(currentStatus.getPrice())) {
                    logger.info("Updating order {} with more aggressive price: {} -> {}", 
                               orderId, currentStatus.getPrice(), aggressivePrice);
                    
                    OrderModifyRequest modifyRequest = new OrderModifyRequest(
                            orderId, symbol, quantity, aggressivePrice);
                    
                    OrderResponse modifiedOrder = orderService.modifyOrder(modifyRequest);
                    
                    if (modifiedOrder.isFilled()) {
                        logger.info("Order {} filled after modification at price {}", 
                                   orderId, modifiedOrder.getAvgPrice());
                        return modifiedOrder;
                    }
                }
                
            } catch (Exception e) {
                logger.warn("Error during aggressive fill monitoring for order {}: {}", orderId, e.getMessage());
                // Continue monitoring despite errors
            }
        }
        
        // Timeout reached - send alert and return current status
        logger.warn("Order {} not filled within {} seconds - sending alert", orderId, config.getOrderTimeoutSeconds());
        
        try {
            OrderResponse finalStatus = orderService.getOrderStatus(symbol, orderId);
            
            String alertMessage = String.format(
                "⚠️ ORDER NOT FILLED WITHIN %d SECONDS\n" +
                "Order ID: %s\n" +
                "Symbol: %s\n" +
                "Side: %s\n" +
                "Quantity: %s\n" +
                "Price: %s\n" +
                "Status: %s\n" +
                "Filled: %s/%s",
                config.getOrderTimeoutSeconds(),
                orderId,
                symbol,
                side,
                quantity,
                finalStatus.getPrice(),
                finalStatus.getStatus(),
                finalStatus.getFilledQuantity() != null ? finalStatus.getFilledQuantity() : "0",
                finalStatus.getOriginalQuantity()
            );
            
            notificationService.sendAlert(alertMessage);
            
            return finalStatus;
            
        } catch (Exception e) {
            logger.error("Failed to get final order status for {}: {}", orderId, e.getMessage());
            throw new RuntimeException("Order timeout and failed to get final status", e);
        }
    }
    
    private BigDecimal calculateAggressivePrice(OrderSide side, OrderBook orderBook) {
        try {
            if (side == OrderSide.SELL) {
                // For sells, use bid price or lower to ensure quick fill
                BigDecimal bestBid = orderBook.getBestBid();
                if (bestBid != null && bestBid.compareTo(BigDecimal.ZERO) > 0) {
                    // Go slightly below best bid for aggressive fill
                    return bestBid.multiply(new BigDecimal("0.999")).setScale(8, RoundingMode.DOWN);
                }
            } else {
                // For buys, use ask price or higher to ensure quick fill
                BigDecimal bestAsk = orderBook.getBestAsk();
                if (bestAsk != null && bestAsk.compareTo(BigDecimal.ZERO) > 0) {
                    // Go slightly above best ask for aggressive fill
                    return bestAsk.multiply(new BigDecimal("1.001")).setScale(8, RoundingMode.UP);
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to calculate aggressive price: {}", e.getMessage());
        }
        
        return null;
    }
    
    public CompletableFuture<OrderResponse> executeOrderAsync(OrderRequest orderRequest) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return executeOrderWithAggressiveFill(orderRequest);
            } catch (Exception e) {
                logger.error("Async order execution failed for {}: {}", orderRequest.getSymbol(), e.getMessage());
                throw new RuntimeException(e);
            }
        });
    }
}