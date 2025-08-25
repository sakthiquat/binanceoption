package com.trading.bot.integration;

import com.trading.bot.exception.*;
import com.trading.bot.service.CircuitBreaker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simplified integration tests for the application components.
 * Tests the basic functionality without complex mocking.
 */
@SpringBootTest
@ActiveProfiles("testing")
public class SimpleIntegrationTest {
    
    @Autowired
    private GlobalExceptionHandler globalExceptionHandler;
    
    @Autowired
    private CircuitBreaker circuitBreaker;
    
    @BeforeEach
    void setUp() {
        globalExceptionHandler.resetErrorCounts();
        circuitBreaker.reset();
    }
    
    @Test
    void testExceptionHierarchy() {
        // Test that all exception types can be created and have proper inheritance
        
        // Base exception
        TradingBotException baseException = new TradingBotException("Base error");
        assertEquals("GENERAL_ERROR", baseException.getErrorCode());
        assertTrue(baseException.isRecoverable());
        
        // API exception
        APIException apiException = new APIException("API error", 500, "SERVER_ERROR");
        assertEquals("API_ERROR", apiException.getErrorCode());
        assertEquals(500, apiException.getHttpStatusCode());
        assertEquals("SERVER_ERROR", apiException.getApiErrorCode());
        
        // Configuration exception
        ConfigurationException configException = new ConfigurationException("Config error", "api-key");
        assertEquals("CONFIG_ERROR", configException.getErrorCode());
        assertFalse(configException.isRecoverable());
        assertEquals("api-key", configException.getConfigurationKey());
        
        // Risk management exception
        RiskManagementException riskException = RiskManagementException.portfolioStopLoss(1500.0, 1000.0);
        assertEquals("RISK_VIOLATION", riskException.getErrorCode());
        assertFalse(riskException.isRecoverable());
        assertEquals("PORTFOLIO_STOP_LOSS", riskException.getRiskType());
        
        // Order execution exception
        OrderExecutionException orderException = OrderExecutionException.orderTimeout("ORDER123", "BTC-CALL", 60);
        assertEquals("ORDER_ERROR", orderException.getErrorCode());
        assertTrue(orderException.isRecoverable());
        assertEquals("ORDER123", orderException.getOrderId());
    }
    
    @Test
    void testCircuitBreakerBasicFunctionality() {
        // Test circuit breaker state transitions
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());
        
        // Simulate failures to open circuit
        for (int i = 0; i < 6; i++) {
            try {
                circuitBreaker.execute(() -> {
                    throw new RuntimeException("Test failure");
                }, "testOperation");
            } catch (APIException e) {
                // Expected
            }
        }
        
        // Circuit should be open after failures
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
        assertTrue(circuitBreaker.getFailureCount() >= 5);
        
        // Reset circuit breaker
        circuitBreaker.reset();
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());
        assertEquals(0, circuitBreaker.getFailureCount());
    }
    
    @Test
    void testGlobalExceptionHandlerBasicFunctionality() {
        // Test that exception handler can process different exception types
        
        APIException apiException = new APIException("Test API error", 500);
        assertDoesNotThrow(() -> {
            globalExceptionHandler.handleAPIException(apiException, "testOperation");
        });
        
        ConfigurationException configException = new ConfigurationException("Test config error");
        assertDoesNotThrow(() -> {
            globalExceptionHandler.handleConfigurationException(configException);
        });
        
        RiskManagementException riskException = RiskManagementException.portfolioStopLoss(2000.0, 1000.0);
        assertDoesNotThrow(() -> {
            globalExceptionHandler.handleRiskManagementException(riskException);
        });
        
        OrderExecutionException orderException = new OrderExecutionException("Test order error");
        assertDoesNotThrow(() -> {
            globalExceptionHandler.handleOrderExecutionException(orderException, "testContext");
        });
    }
    
    @Test
    void testExceptionFormattedMessages() {
        // Test that formatted messages contain expected information
        
        APIException apiException = new APIException("Connection failed", 503, "SERVICE_UNAVAILABLE");
        String formatted = apiException.getFormattedMessage();
        assertTrue(formatted.contains("API_ERROR"));
        assertTrue(formatted.contains("503"));
        assertTrue(formatted.contains("SERVICE_UNAVAILABLE"));
        
        RiskManagementException riskException = RiskManagementException.positionStopLoss("POS123", -35.0, -30.0);
        String riskFormatted = riskException.getFormattedMessage();
        assertTrue(riskFormatted.contains("POSITION_STOP_LOSS"));
        assertTrue(riskFormatted.contains("-35.00"));
        assertTrue(riskFormatted.contains("-30.00"));
    }
    
    @Test
    void testAPIExceptionSpecialMethods() {
        // Test API exception helper methods
        
        APIException rateLimitError = new APIException("Rate limit exceeded", 429, "RATE_LIMIT_EXCEEDED");
        assertTrue(rateLimitError.isRateLimitError());
        assertFalse(rateLimitError.isAuthenticationError());
        
        APIException authError = new APIException("Invalid signature", 401, "INVALID_SIGNATURE");
        assertFalse(authError.isRateLimitError());
        assertTrue(authError.isAuthenticationError());
        
        APIException serverError = new APIException("Internal server error", 500, "INTERNAL_ERROR");
        assertFalse(serverError.isRateLimitError());
        assertFalse(serverError.isAuthenticationError());
    }
    
    @Test
    void testCircuitBreakerStatusInfo() {
        // Test circuit breaker status information
        String initialStatus = circuitBreaker.getStatusInfo();
        assertTrue(initialStatus.contains("CLOSED"));
        assertTrue(initialStatus.contains("Failures=0"));
        
        // Cause some failures
        for (int i = 0; i < 3; i++) {
            try {
                circuitBreaker.execute(() -> {
                    throw new RuntimeException("Test failure");
                }, "testOperation");
            } catch (APIException e) {
                // Expected
            }
        }
        
        String statusAfterFailures = circuitBreaker.getStatusInfo();
        assertTrue(statusAfterFailures.contains("Failures=3"));
    }
    
    @Test
    void testExceptionRecoverability() {
        // Test that exceptions have correct recoverability settings
        
        // Recoverable exceptions
        APIException apiException = new APIException("Temporary error", 503);
        assertTrue(apiException.isRecoverable());
        
        OrderExecutionException orderException = new OrderExecutionException("Order failed");
        assertTrue(orderException.isRecoverable());
        
        // Non-recoverable exceptions
        ConfigurationException configException = new ConfigurationException("Invalid config");
        assertFalse(configException.isRecoverable());
        
        RiskManagementException riskException = RiskManagementException.portfolioStopLoss(2000.0, 1000.0);
        assertFalse(riskException.isRecoverable());
        
        // Client errors should be non-recoverable
        APIException clientError = new APIException("Bad request", 400);
        assertFalse(clientError.isRecoverable());
    }
}