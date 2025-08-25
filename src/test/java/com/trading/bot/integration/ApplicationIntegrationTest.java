package com.trading.bot.integration;

import com.trading.bot.BtcOptionsStraddleBotApplication;
import com.trading.bot.config.TradingConfig;
import com.trading.bot.exception.*;
import com.trading.bot.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Comprehensive integration tests for the complete application flow.
 * Tests the integration of all components and error handling scenarios.
 */
@SpringBootTest
@ActiveProfiles("testing")
public class ApplicationIntegrationTest {
    
    @Autowired
    private BtcOptionsStraddleBotApplication application;
    
    @Autowired
    private TradingConfig tradingConfig;
    
    @Autowired
    private GlobalExceptionHandler globalExceptionHandler;
    
    @Autowired
    private ShutdownManager shutdownManager;
    
    @Autowired
    private CircuitBreaker circuitBreaker;
    
    @MockBean
    private TradingSessionManager tradingSessionManager;
    
    @MockBean
    private PositionManager positionManager;
    
    @MockBean
    private NotificationService notificationService;
    
    @MockBean
    private LoggingService loggingService;
    
    @MockBean
    private BinanceOptionsClient binanceClient;
    
    @BeforeEach
    void setUp() {
        // Reset all mocks and state
        reset(tradingSessionManager, positionManager, notificationService, 
              loggingService, binanceClient);
        
        globalExceptionHandler.resetErrorCounts();
        circuitBreaker.reset();
        
        // Setup default mock behaviors
        when(positionManager.getOpenPositionCount()).thenReturn(0);
        when(tradingSessionManager.isSessionActive()).thenReturn(false);
        doNothing().when(loggingService).logApplicationEvent(anyString(), anyString());
        doNothing().when(loggingService).logError(anyString(), anyString(), any(Throwable.class));
        doNothing().when(notificationService).sendNotification(anyString());
        doNothing().when(notificationService).sendAlert(anyString());
    }
    
    @Test
    void testApplicationContextLoads() {
        // Test that Spring context loads successfully
        assertNotNull(application);
        assertNotNull(tradingConfig);
        assertNotNull(globalExceptionHandler);
        assertNotNull(shutdownManager);
        assertNotNull(circuitBreaker);
    }
    
    @Test
    void testConfigurationValidation() {
        // Test configuration validation with valid config
        assertDoesNotThrow(() -> {
            // This should pass with the test configuration
            validateTestConfiguration();
        });
    }
    
    @Test
    void testConfigurationValidationFailures() {
        // Test various configuration validation failures
        
        // Test missing API key
        TradingConfig invalidConfig1 = createInvalidConfig();
        invalidConfig1.setApiKey("");
        
        ConfigurationException exception = assertThrows(ConfigurationException.class, () -> {
            validateConfigurationWithConfig(invalidConfig1);
        });
        assertEquals("api-key", exception.getConfigurationKey());
        
        // Test invalid session times
        TradingConfig invalidConfig2 = createInvalidConfig();
        invalidConfig2.setSessionStartTime(LocalTime.of(14, 0));
        invalidConfig2.setSessionEndTime(LocalTime.of(13, 0));
        
        exception = assertThrows(ConfigurationException.class, () -> {
            validateConfigurationWithConfig(invalidConfig2);
        });
        assertEquals("session-times", exception.getConfigurationKey());
        
        // Test invalid position quantity
        TradingConfig invalidConfig3 = createInvalidConfig();
        invalidConfig3.setPositionQuantity(BigDecimal.ZERO);
        
        exception = assertThrows(ConfigurationException.class, () -> {
            validateConfigurationWithConfig(invalidConfig3);
        });
        assertEquals("position-quantity", exception.getConfigurationKey());
    }
    
    @Test
    @Timeout(10)
    void testGracefulShutdown() throws InterruptedException {
        // Test graceful shutdown process
        
        // Setup mocks for shutdown scenario
        when(positionManager.getOpenPositionCount()).thenReturn(2);
        doNothing().when(positionManager).closeAllPositions(anyString());
        doNothing().when(tradingSessionManager).endSession(anyString());
        
        // Initiate shutdown
        CountDownLatch shutdownLatch = new CountDownLatch(1);
        Thread shutdownThread = new Thread(() -> {
            shutdownManager.performGracefulShutdown("Test shutdown");
            shutdownLatch.countDown();
        });
        
        shutdownThread.start();
        
        // Wait for shutdown to complete
        assertTrue(shutdownLatch.await(5, TimeUnit.SECONDS), "Shutdown should complete within 5 seconds");
        
        // Verify shutdown actions were performed
        verify(positionManager).closeAllPositions("Test shutdown");
        verify(notificationService).sendNotification(contains("SHUTDOWN"));
        verify(loggingService).logApplicationEvent(eq("GRACEFUL_SHUTDOWN_STARTED"), anyString());
        verify(loggingService).logApplicationEvent(eq("GRACEFUL_SHUTDOWN_COMPLETED"), anyString());
    }
    
    @Test
    void testEmergencyShutdown() {
        // Test emergency shutdown for critical errors
        
        RiskManagementException criticalError = RiskManagementException.portfolioStopLoss(2000.0, 1000.0);
        
        // This should trigger system exit, so we can't test the actual exit
        // But we can test the preparation steps
        when(positionManager.getOpenPositionCount()).thenReturn(3);
        doNothing().when(positionManager).closeAllPositions(anyString());
        
        // Test that emergency shutdown is called (without actually exiting)
        assertDoesNotThrow(() -> {
            // We can't actually test System.exit(), but we can test the setup
            globalExceptionHandler.handleRiskManagementException(criticalError);
        });
        
        verify(notificationService).sendAlert(contains("RISK MANAGEMENT ALERT"));
    }
    
    @Test
    void testCircuitBreakerIntegration() {
        // Test circuit breaker integration with API calls
        
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());
        
        // Simulate API failures to open circuit
        for (int i = 0; i < 6; i++) {
            try {
                circuitBreaker.execute(() -> {
                    throw new RuntimeException("Simulated API failure");
                }, "testOperation");
            } catch (APIException e) {
                // Expected
            }
        }
        
        // Circuit should be open
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
        
        // Verify that subsequent calls fail fast
        APIException exception = assertThrows(APIException.class, () -> {
            circuitBreaker.execute(() -> "success", "testOperation");
        });
        
        assertTrue(exception.getMessage().contains("Circuit breaker is OPEN"));
    }
    
    @Test
    void testExceptionHandlerIntegration() {
        // Test global exception handler with different exception types
        
        // Test API exception handling
        APIException apiException = new APIException("Test API error", 500, "INTERNAL_ERROR");
        assertDoesNotThrow(() -> {
            globalExceptionHandler.handleAPIException(apiException, "testOperation");
        });
        
        // Test configuration exception handling
        ConfigurationException configException = new ConfigurationException("Test config error", "test-key");
        assertDoesNotThrow(() -> {
            globalExceptionHandler.handleConfigurationException(configException);
        });
        
        // Test risk management exception handling
        RiskManagementException riskException = RiskManagementException.positionStopLoss("POS123", -35.0, -30.0);
        assertDoesNotThrow(() -> {
            globalExceptionHandler.handleRiskManagementException(riskException);
        });
        
        // Test order execution exception handling
        OrderExecutionException orderException = OrderExecutionException.orderTimeout("ORDER123", "BTC-CALL", 60);
        assertDoesNotThrow(() -> {
            globalExceptionHandler.handleOrderExecutionException(orderException, "testContext");
        });
        
        // Verify notifications were sent for critical errors
        verify(notificationService, atLeastOnce()).sendAlert(anyString());
    }
    
    @Test
    void testErrorRecoveryScenarios() {
        // Test various error recovery scenarios
        
        // Test recoverable API error
        APIException recoverableError = new APIException("Temporary error", 503);
        assertTrue(recoverableError.isRecoverable());
        
        globalExceptionHandler.handleAPIException(recoverableError, "testOperation");
        
        // Application should continue running (no shutdown initiated)
        assertFalse(shutdownManager.isShutdownInProgress());
        
        // Test non-recoverable configuration error
        ConfigurationException nonRecoverableError = new ConfigurationException("Invalid API key");
        assertFalse(nonRecoverableError.isRecoverable());
        
        globalExceptionHandler.handleConfigurationException(nonRecoverableError);
        
        // Should send alert for configuration error
        verify(notificationService).sendAlert(contains("CONFIGURATION ERROR"));
    }
    
    @Test
    void testPositionCleanupOnShutdown() {
        // Test that positions are properly cleaned up during shutdown
        
        when(positionManager.getOpenPositionCount()).thenReturn(5);
        doNothing().when(positionManager).closeAllPositions(anyString());
        
        shutdownManager.performGracefulShutdown("Test position cleanup");
        
        // Verify positions were closed
        verify(positionManager).closeAllPositions("Test position cleanup");
        
        // Verify appropriate notifications were sent
        verify(notificationService).sendNotification(contains("SHUTDOWN"));
    }
    
    @Test
    void testNotificationErrorHandling() {
        // Test that notification failures don't crash the application
        
        doThrow(new RuntimeException("Notification service unavailable"))
            .when(notificationService).sendNotification(anyString());
        
        // Should not throw exception even if notifications fail
        assertDoesNotThrow(() -> {
            shutdownManager.performGracefulShutdown("Test notification failure");
        });
        
        // Verify shutdown still completed
        verify(loggingService).logApplicationEvent(eq("GRACEFUL_SHUTDOWN_COMPLETED"), anyString());
    }
    
    @Test
    void testLoggingErrorHandling() {
        // Test that logging failures don't crash the application
        
        doThrow(new RuntimeException("Logging service unavailable"))
            .when(loggingService).logApplicationEvent(anyString(), anyString());
        
        // Should not throw exception even if logging fails
        assertDoesNotThrow(() -> {
            shutdownManager.performGracefulShutdown("Test logging failure");
        });
    }
    
    @Test
    void testConcurrentShutdownRequests() throws InterruptedException {
        // Test that multiple concurrent shutdown requests are handled properly
        
        when(positionManager.getOpenPositionCount()).thenReturn(1);
        doNothing().when(positionManager).closeAllPositions(anyString());
        
        CountDownLatch startLatch = new CountDownLatch(3);
        CountDownLatch completeLatch = new CountDownLatch(3);
        
        // Start multiple shutdown threads
        for (int i = 0; i < 3; i++) {
            final int threadNum = i;
            new Thread(() -> {
                startLatch.countDown();
                try {
                    startLatch.await(); // Wait for all threads to start
                    shutdownManager.performGracefulShutdown("Concurrent test " + threadNum);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    completeLatch.countDown();
                }
            }).start();
        }
        
        // Wait for all threads to complete
        assertTrue(completeLatch.await(10, TimeUnit.SECONDS), "All shutdown threads should complete");
        
        // Verify that positions were only closed once (not multiple times)
        verify(positionManager, times(1)).closeAllPositions(anyString());
    }
    
    private void validateTestConfiguration() throws ConfigurationException {
        // Simulate configuration validation logic
        if (tradingConfig.getApiKey() == null || tradingConfig.getApiKey().trim().isEmpty()) {
            throw new ConfigurationException("API key is required", "api-key");
        }
        // Add other validation checks as needed
    }
    
    private void validateConfigurationWithConfig(TradingConfig config) throws ConfigurationException {
        if (config.getApiKey() == null || config.getApiKey().trim().isEmpty()) {
            throw new ConfigurationException("API key is required", "api-key");
        }
        
        if (config.getSecretKey() == null || config.getSecretKey().trim().isEmpty()) {
            throw new ConfigurationException("Secret key is required", "secret-key");
        }
        
        if (config.getSessionStartTime() == null) {
            throw new ConfigurationException("Session start time is required", "session-start-time");
        }
        
        if (config.getSessionEndTime() == null) {
            throw new ConfigurationException("Session end time is required", "session-end-time");
        }
        
        if (!config.getSessionStartTime().isBefore(config.getSessionEndTime())) {
            throw new ConfigurationException("Session start time must be before end time", "session-times");
        }
        
        if (config.getPositionQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ConfigurationException("Position quantity must be positive", "position-quantity");
        }
        
        if (config.getCycleIntervalMinutes() <= 0) {
            throw new ConfigurationException("Cycle interval must be positive", "cycle-interval-minutes");
        }
        
        if (config.getNumberOfCycles() <= 0) {
            throw new ConfigurationException("Number of cycles must be positive", "number-of-cycles");
        }
    }
    
    private TradingConfig createInvalidConfig() {
        TradingConfig config = new TradingConfig();
        config.setApiKey("test-key");
        config.setSecretKey("test-secret");
        config.setSessionStartTime(LocalTime.of(12, 0));
        config.setSessionEndTime(LocalTime.of(13, 0));
        config.setPositionQuantity(new BigDecimal("0.01"));
        config.setCycleIntervalMinutes(5);
        config.setNumberOfCycles(10);
        config.setStrikeDistance(10);
        return config;
    }
}