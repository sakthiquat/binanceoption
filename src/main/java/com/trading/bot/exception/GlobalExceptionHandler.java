package com.trading.bot.exception;

import com.trading.bot.service.LoggingService;
import com.trading.bot.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Global exception handler for the trading bot application.
 * Provides centralized error handling, logging, and notification.
 */
@Component
public class GlobalExceptionHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    @Autowired
    private LoggingService loggingService;
    
    @Autowired
    private NotificationService notificationService;
    
    // Error tracking for rate limiting notifications
    private final ConcurrentHashMap<String, AtomicInteger> errorCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> lastNotificationTime = new ConcurrentHashMap<>();
    
    // Configuration
    private final int maxErrorsBeforeAlert = 3;
    private final long notificationCooldownMinutes = 5;
    
    @PostConstruct
    public void initialize() {
        logger.info("Global exception handler initialized");
        
        // Set up uncaught exception handler for threads
        Thread.setDefaultUncaughtExceptionHandler(this::handleUncaughtException);
    }
    
    /**
     * Handle trading bot exceptions with appropriate logging and notifications
     */
    public void handleTradingBotException(TradingBotException e, String context) {
        String errorKey = e.getErrorCode() + "_" + context;
        
        // Log the exception
        loggingService.logError(e.getErrorCode(), e.getFormattedMessage(), e);
        logger.error("TradingBotException in context '{}': {}", context, e.getFormattedMessage(), e);
        
        // Track error frequency
        AtomicInteger count = errorCounts.computeIfAbsent(errorKey, k -> new AtomicInteger(0));
        int currentCount = count.incrementAndGet();
        
        // Send notification if threshold reached and cooldown period passed
        if (shouldSendNotification(errorKey, currentCount)) {
            sendErrorNotification(e, context, currentCount);
        }
        
        // Handle specific exception types
        handleSpecificException(e, context);
    }
    
    /**
     * Handle API exceptions with retry logic consideration
     */
    public void handleAPIException(APIException e, String operation) {
        String context = "API_" + operation;
        
        // Log API-specific details
        logger.error("API Exception for operation '{}': HTTP {}, API Error: {}, Message: {}", 
                    operation, e.getHttpStatusCode(), e.getApiErrorCode(), e.getMessage());
        
        loggingService.logError("API_ERROR", 
            String.format("Operation: %s, HTTP: %d, API Error: %s, Message: %s", 
                         operation, e.getHttpStatusCode(), e.getApiErrorCode(), e.getMessage()), e);
        
        // Handle specific API error types
        if (e.isRateLimitError()) {
            handleRateLimitError(e, operation);
        } else if (e.isAuthenticationError()) {
            handleAuthenticationError(e, operation);
        } else {
            handleTradingBotException(e, context);
        }
    }
    
    /**
     * Handle configuration exceptions (typically fatal)
     */
    public void handleConfigurationException(ConfigurationException e) {
        logger.error("Configuration Exception: {}", e.getFormattedMessage(), e);
        loggingService.logError("CONFIG_ERROR", e.getFormattedMessage(), e);
        
        // Configuration errors are typically fatal
        String alertMessage = String.format(
            "🚨 CRITICAL CONFIGURATION ERROR\n" +
            "Error: %s\n" +
            "Configuration Key: %s\n" +
            "Application may need to be restarted with correct configuration",
            e.getMessage(),
            e.getConfigurationKey() != null ? e.getConfigurationKey() : "Unknown"
        );
        
        notificationService.sendAlert(alertMessage);
    }
    
    /**
     * Handle risk management exceptions (critical)
     */
    public void handleRiskManagementException(RiskManagementException e) {
        logger.error("Risk Management Exception: {}", e.getFormattedMessage(), e);
        loggingService.logError("RISK_VIOLATION", e.getFormattedMessage(), e);
        
        // Risk violations require immediate attention
        String alertMessage = String.format(
            "🚨 RISK MANAGEMENT ALERT\n" +
            "Risk Type: %s\n" +
            "Current Value: %.2f\n" +
            "Threshold: %.2f\n" +
            "Action: %s",
            e.getRiskType(),
            e.getCurrentValue(),
            e.getThreshold(),
            e.getMessage()
        );
        
        notificationService.sendAlert(alertMessage);
    }
    
    /**
     * Handle order execution exceptions
     */
    public void handleOrderExecutionException(OrderExecutionException e, String context) {
        logger.error("Order Execution Exception in context '{}': {}", context, e.getFormattedMessage(), e);
        loggingService.logError("ORDER_ERROR", e.getFormattedMessage(), e);
        
        // Send notification for order failures
        if (e.getOrderId() != null) {
            String alertMessage = String.format(
                "⚠️ ORDER EXECUTION ERROR\n" +
                "Order ID: %s\n" +
                "Symbol: %s\n" +
                "Type: %s\n" +
                "Error: %s\n" +
                "Recoverable: %s",
                e.getOrderId(),
                e.getSymbol() != null ? e.getSymbol() : "Unknown",
                e.getOrderType() != null ? e.getOrderType() : "Unknown",
                e.getMessage(),
                e.isRecoverable() ? "Yes" : "No"
            );
            
            notificationService.sendAlert(alertMessage);
        }
    }
    
    /**
     * Handle uncaught exceptions in threads
     */
    private void handleUncaughtException(Thread thread, Throwable throwable) {
        logger.error("Uncaught exception in thread '{}': {}", thread.getName(), throwable.getMessage(), throwable);
        loggingService.logError("UNCAUGHT_EXCEPTION", 
            String.format("Thread: %s, Error: %s", thread.getName(), throwable.getMessage()), throwable);
        
        String alertMessage = String.format(
            "🚨 UNCAUGHT EXCEPTION\n" +
            "Thread: %s\n" +
            "Error: %s\n" +
            "This may indicate a serious application issue",
            thread.getName(),
            throwable.getMessage()
        );
        
        notificationService.sendAlert(alertMessage);
    }
    
    /**
     * Handle rate limit errors specifically
     */
    private void handleRateLimitError(APIException e, String operation) {
        logger.warn("Rate limit exceeded for operation '{}': {}", operation, e.getMessage());
        
        String alertMessage = String.format(
            "⚠️ API RATE LIMIT EXCEEDED\n" +
            "Operation: %s\n" +
            "HTTP Status: %d\n" +
            "The bot will automatically retry with backoff",
            operation,
            e.getHttpStatusCode()
        );
        
        // Rate limit notifications have longer cooldown
        String rateKey = "RATE_LIMIT_" + operation;
        if (shouldSendNotification(rateKey, 1, notificationCooldownMinutes * 2)) {
            notificationService.sendAlert(alertMessage);
            lastNotificationTime.put(rateKey, System.currentTimeMillis());
        }
    }
    
    /**
     * Handle authentication errors (critical)
     */
    private void handleAuthenticationError(APIException e, String operation) {
        logger.error("Authentication error for operation '{}': {}", operation, e.getMessage());
        
        String alertMessage = String.format(
            "🚨 API AUTHENTICATION ERROR\n" +
            "Operation: %s\n" +
            "HTTP Status: %d\n" +
            "API Error: %s\n" +
            "Check API keys and permissions",
            operation,
            e.getHttpStatusCode(),
            e.getApiErrorCode()
        );
        
        notificationService.sendAlert(alertMessage);
    }
    
    /**
     * Handle specific exception types with custom logic
     */
    private void handleSpecificException(TradingBotException e, String context) {
        if (e instanceof RiskManagementException) {
            handleRiskManagementException((RiskManagementException) e);
        } else if (e instanceof OrderExecutionException) {
            handleOrderExecutionException((OrderExecutionException) e, context);
        } else if (e instanceof ConfigurationException) {
            handleConfigurationException((ConfigurationException) e);
        }
        // APIException is handled separately in handleAPIException
    }
    
    /**
     * Determine if a notification should be sent based on error frequency and cooldown
     */
    private boolean shouldSendNotification(String errorKey, int currentCount) {
        return shouldSendNotification(errorKey, currentCount, notificationCooldownMinutes);
    }
    
    private boolean shouldSendNotification(String errorKey, int currentCount, long cooldownMinutes) {
        if (currentCount < maxErrorsBeforeAlert) {
            return false;
        }
        
        Long lastNotification = lastNotificationTime.get(errorKey);
        if (lastNotification == null) {
            lastNotificationTime.put(errorKey, System.currentTimeMillis());
            return true;
        }
        
        long timeSinceLastNotification = System.currentTimeMillis() - lastNotification;
        long cooldownMillis = cooldownMinutes * 60 * 1000;
        
        if (timeSinceLastNotification > cooldownMillis) {
            lastNotificationTime.put(errorKey, System.currentTimeMillis());
            return true;
        }
        
        return false;
    }
    
    /**
     * Send error notification with context
     */
    private void sendErrorNotification(TradingBotException e, String context, int errorCount) {
        String alertMessage = String.format(
            "⚠️ REPEATED ERROR DETECTED\n" +
            "Context: %s\n" +
            "Error Code: %s\n" +
            "Count: %d (last %d minutes)\n" +
            "Message: %s\n" +
            "Recoverable: %s",
            context,
            e.getErrorCode(),
            errorCount,
            notificationCooldownMinutes,
            e.getMessage(),
            e.isRecoverable() ? "Yes" : "No"
        );
        
        notificationService.sendAlert(alertMessage);
    }
    
    /**
     * Get error statistics for monitoring
     */
    public String getErrorStatistics() {
        StringBuilder stats = new StringBuilder();
        stats.append("Error Statistics:\n");
        
        errorCounts.forEach((key, count) -> {
            stats.append(String.format("  %s: %d errors\n", key, count.get()));
        });
        
        return stats.toString();
    }
    
    /**
     * Reset error counts (for testing or administrative purposes)
     */
    public void resetErrorCounts() {
        errorCounts.clear();
        lastNotificationTime.clear();
        logger.info("Error counts and notification timestamps reset");
    }
}