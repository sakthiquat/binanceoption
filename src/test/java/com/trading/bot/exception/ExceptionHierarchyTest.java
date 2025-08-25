package com.trading.bot.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the exception hierarchy without Spring Boot context.
 * Tests the basic functionality of all exception classes.
 */
public class ExceptionHierarchyTest {
    
    @Test
    void testTradingBotExceptionBasicFunctionality() {
        // Test basic constructor
        TradingBotException exception = new TradingBotException("Test error");
        assertEquals("Test error", exception.getMessage());
        assertEquals("GENERAL_ERROR", exception.getErrorCode());
        assertTrue(exception.isRecoverable());
        
        // Test constructor with cause
        RuntimeException cause = new RuntimeException("Root cause");
        TradingBotException exceptionWithCause = new TradingBotException("Test error", cause);
        assertEquals("Test error", exceptionWithCause.getMessage());
        assertEquals(cause, exceptionWithCause.getCause());
        
        // Test constructor with custom error code and recoverability
        TradingBotException customException = new TradingBotException("Custom error", "CUSTOM_ERROR", false);
        assertEquals("Custom error", customException.getMessage());
        assertEquals("CUSTOM_ERROR", customException.getErrorCode());
        assertFalse(customException.isRecoverable());
        
        // Test formatted message
        String formatted = customException.getFormattedMessage();
        assertTrue(formatted.contains("CUSTOM_ERROR"));
        assertTrue(formatted.contains("Custom error"));
        assertTrue(formatted.contains("Recoverable: false"));
    }
    
    @Test
    void testAPIExceptionFunctionality() {
        // Test basic API exception
        APIException apiException = new APIException("API error");
        assertEquals("API error", apiException.getMessage());
        assertEquals("API_ERROR", apiException.getErrorCode());
        assertTrue(apiException.isRecoverable());
        assertEquals(-1, apiException.getHttpStatusCode());
        assertNull(apiException.getApiErrorCode());
        
        // Test API exception with HTTP status
        APIException httpException = new APIException("Server error", 500);
        assertEquals(500, httpException.getHttpStatusCode());
        assertFalse(httpException.isRecoverable()); // 5xx errors are not recoverable
        
        // Test API exception with full details
        APIException fullException = new APIException("Rate limit", 429, "RATE_LIMIT_EXCEEDED");
        assertEquals(429, fullException.getHttpStatusCode());
        assertEquals("RATE_LIMIT_EXCEEDED", fullException.getApiErrorCode());
        assertTrue(fullException.isRecoverable()); // Rate limit errors are recoverable
        
        // Test formatted message
        String formatted = fullException.getFormattedMessage();
        assertTrue(formatted.contains("HTTP Status: 429"));
        assertTrue(formatted.contains("API Error: RATE_LIMIT_EXCEEDED"));
        
        // Test helper methods
        assertTrue(fullException.isRateLimitError());
        assertFalse(fullException.isAuthenticationError());
        
        APIException authException = new APIException("Invalid signature", 401, "INVALID_SIGNATURE");
        assertFalse(authException.isRateLimitError());
        assertTrue(authException.isAuthenticationError());
    }
    
    @Test
    void testConfigurationExceptionFunctionality() {
        // Test basic configuration exception
        ConfigurationException configException = new ConfigurationException("Config error");
        assertEquals("Config error", configException.getMessage());
        assertEquals("CONFIG_ERROR", configException.getErrorCode());
        assertFalse(configException.isRecoverable());
        assertNull(configException.getConfigurationKey());
        
        // Test configuration exception with key
        ConfigurationException keyException = new ConfigurationException("Invalid API key", "api-key");
        assertEquals("Invalid API key", keyException.getMessage());
        assertEquals("api-key", keyException.getConfigurationKey());
        
        // Test formatted message
        String formatted = keyException.getFormattedMessage();
        assertTrue(formatted.contains("Configuration Key: api-key"));
        
        // Test with cause
        RuntimeException cause = new RuntimeException("File not found");
        ConfigurationException causeException = new ConfigurationException("Config load failed", cause, "config-file");
        assertEquals(cause, causeException.getCause());
        assertEquals("config-file", causeException.getConfigurationKey());
    }
    
    @Test
    void testRiskManagementExceptionFunctionality() {
        // Test basic risk management exception
        RiskManagementException riskException = new RiskManagementException("Risk violation");
        assertEquals("Risk violation", riskException.getMessage());
        assertEquals("RISK_VIOLATION", riskException.getErrorCode());
        assertFalse(riskException.isRecoverable());
        assertEquals("UNKNOWN", riskException.getRiskType());
        
        // Test risk management exception with details
        RiskManagementException detailedException = new RiskManagementException(
            "Portfolio stop loss", "PORTFOLIO_STOP_LOSS", 1500.0, 1000.0);
        assertEquals("PORTFOLIO_STOP_LOSS", detailedException.getRiskType());
        assertEquals(1500.0, detailedException.getCurrentValue());
        assertEquals(1000.0, detailedException.getThreshold());
        
        // Test formatted message
        String formatted = detailedException.getFormattedMessage();
        assertTrue(formatted.contains("Risk Type: PORTFOLIO_STOP_LOSS"));
        assertTrue(formatted.contains("Current: 1500.00"));
        assertTrue(formatted.contains("Threshold: 1000.00"));
        
        // Test static factory methods
        RiskManagementException portfolioException = RiskManagementException.portfolioStopLoss(2000.0, 1500.0);
        assertEquals("PORTFOLIO_STOP_LOSS", portfolioException.getRiskType());
        assertTrue(portfolioException.getMessage().contains("Portfolio stop-loss triggered"));
        
        RiskManagementException positionException = RiskManagementException.positionStopLoss("POS123", -35.0, -30.0);
        assertEquals("POSITION_STOP_LOSS", positionException.getRiskType());
        assertTrue(positionException.getMessage().contains("Position POS123 stop-loss triggered"));
    }
    
    @Test
    void testOrderExecutionExceptionFunctionality() {
        // Test basic order execution exception
        OrderExecutionException orderException = new OrderExecutionException("Order failed");
        assertEquals("Order failed", orderException.getMessage());
        assertEquals("ORDER_ERROR", orderException.getErrorCode());
        assertTrue(orderException.isRecoverable());
        assertNull(orderException.getOrderId());
        
        // Test order execution exception with details
        OrderExecutionException detailedException = new OrderExecutionException(
            "Order timeout", "ORDER123", "LIMIT", "BTC-CALL");
        assertEquals("ORDER123", detailedException.getOrderId());
        assertEquals("LIMIT", detailedException.getOrderType());
        assertEquals("BTC-CALL", detailedException.getSymbol());
        
        // Test formatted message
        String formatted = detailedException.getFormattedMessage();
        assertTrue(formatted.contains("Order ID: ORDER123"));
        assertTrue(formatted.contains("Type: LIMIT"));
        assertTrue(formatted.contains("Symbol: BTC-CALL"));
        
        // Test constructor with recoverability
        OrderExecutionException nonRecoverableException = new OrderExecutionException("Fatal error", false);
        assertFalse(nonRecoverableException.isRecoverable());
        
        // Test constructor with error code
        OrderExecutionException customCodeException = new OrderExecutionException(
            "Custom error", "CUSTOM_ORDER_ERROR", false);
        assertEquals("CUSTOM_ORDER_ERROR", customCodeException.getErrorCode());
        
        // Test static factory methods
        OrderExecutionException timeoutException = OrderExecutionException.orderTimeout("ORDER456", "BTC-PUT", 60);
        assertEquals("ORDER456", timeoutException.getOrderId());
        assertEquals("BTC-PUT", timeoutException.getSymbol());
        assertTrue(timeoutException.getMessage().contains("timed out after 60 seconds"));
        
        OrderExecutionException balanceException = OrderExecutionException.insufficientBalance("BTC-CALL", "0.005");
        assertFalse(balanceException.isRecoverable());
        assertTrue(balanceException.getMessage().contains("Insufficient balance"));
    }
    
    @Test
    void testExceptionInheritance() {
        // Test that all custom exceptions inherit from TradingBotException
        APIException apiException = new APIException("API error");
        assertTrue(apiException instanceof TradingBotException);
        
        ConfigurationException configException = new ConfigurationException("Config error");
        assertTrue(configException instanceof TradingBotException);
        
        RiskManagementException riskException = new RiskManagementException("Risk error");
        assertTrue(riskException instanceof TradingBotException);
        
        OrderExecutionException orderException = new OrderExecutionException("Order error");
        assertTrue(orderException instanceof TradingBotException);
        
        // Test that all inherit from Exception
        assertTrue(apiException instanceof Exception);
        assertTrue(configException instanceof Exception);
        assertTrue(riskException instanceof Exception);
        assertTrue(orderException instanceof Exception);
    }
    
    @Test
    void testRecoverabilityLogic() {
        // Test that recoverability is set correctly based on error types
        
        // API exceptions: Most 4xx and 5xx are not recoverable, except rate limits
        APIException clientError = new APIException("Bad request", 400);
        assertFalse(clientError.isRecoverable());
        
        APIException serverError = new APIException("Server error", 500);
        assertFalse(serverError.isRecoverable());
        
        APIException rateLimitError = new APIException("Rate limit", 429);
        assertTrue(rateLimitError.isRecoverable());
        
        // Configuration exceptions are never recoverable
        ConfigurationException configError = new ConfigurationException("Config error");
        assertFalse(configError.isRecoverable());
        
        // Risk management exceptions are never recoverable
        RiskManagementException riskError = new RiskManagementException("Risk error");
        assertFalse(riskError.isRecoverable());
        
        // Order execution exceptions are recoverable by default
        OrderExecutionException orderError = new OrderExecutionException("Order error");
        assertTrue(orderError.isRecoverable());
        
        // But can be set to non-recoverable
        OrderExecutionException fatalOrderError = new OrderExecutionException("Fatal error", false);
        assertFalse(fatalOrderError.isRecoverable());
    }
}