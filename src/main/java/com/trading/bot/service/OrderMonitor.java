package com.trading.bot.service;

import com.trading.bot.model.OrderResponse;
import com.trading.bot.model.OrderStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class OrderMonitor {
    
    private static final Logger logger = LoggerFactory.getLogger(OrderMonitor.class);
    
    @Autowired
    private OrderService orderService;
    
    private final Map<String, OrderResponse> activeOrders = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    
    public void addOrderToMonitor(OrderResponse order) {
        if (!order.isFilled()) {
            activeOrders.put(order.getOrderId(), order);
            logger.info("Added order {} to monitoring", order.getOrderId());
        }
    }
    
    public void removeOrderFromMonitor(String orderId) {
        OrderResponse removed = activeOrders.remove(orderId);
        if (removed != null) {
            logger.info("Removed order {} from monitoring", orderId);
        }
    }
    
    public OrderResponse getOrderStatus(String orderId) {
        return activeOrders.get(orderId);
    }
    
    public void startMonitoring() {
        logger.info("Starting order monitoring service");
        
        scheduler.scheduleAtFixedRate(this::checkOrderStatuses, 0, 5, TimeUnit.SECONDS);
    }
    
    public void stopMonitoring() {
        logger.info("Stopping order monitoring service");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    private void checkOrderStatuses() {
        if (activeOrders.isEmpty()) {
            return;
        }
        
        logger.debug("Checking status of {} active orders", activeOrders.size());
        
        activeOrders.entrySet().removeIf(entry -> {
            String orderId = entry.getKey();
            OrderResponse order = entry.getValue();
            
            try {
                OrderResponse currentStatus = orderService.getOrderStatus(order.getSymbol(), orderId);
                
                if (currentStatus.isFilled()) {
                    logger.info("Order {} completed: {} @ {}", 
                               orderId, currentStatus.getFilledQuantity(), currentStatus.getAvgPrice());
                    return true; // Remove from monitoring
                }
                
                if (currentStatus.getStatus() == OrderStatus.CANCELED || 
                    currentStatus.getStatus() == OrderStatus.REJECTED ||
                    currentStatus.getStatus() == OrderStatus.EXPIRED) {
                    logger.warn("Order {} terminated with status: {}", orderId, currentStatus.getStatus());
                    return true; // Remove from monitoring
                }
                
                // Update the order in our map
                activeOrders.put(orderId, currentStatus);
                
            } catch (Exception e) {
                logger.warn("Failed to check status for order {}: {}", orderId, e.getMessage());
            }
            
            return false; // Keep monitoring
        });
    }
    
    public int getActiveOrderCount() {
        return activeOrders.size();
    }
    
    public boolean hasActiveOrders() {
        return !activeOrders.isEmpty();
    }
}