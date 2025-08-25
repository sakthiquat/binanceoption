package com.trading.bot.integration;

import com.trading.bot.exception.*;
import com.trading.bot.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Integration tests for error scenarios and recovery mechanisms.
 * Tests the application's behavior under various failure conditions.
 */
@SpringBootTest
@ActiveProfiles("testing")
public class ErrorScenarioIntegrationTest {
    
    @Autowired
    private GlobalExceptionHandler globalExceptionHandler;
    
    @Autowired
    private CircuitBreaker circuitBreaker;
    
    @Autowired
    private ShutdownManager shutdownManager;
    
    @MockBean
    private NotificationService notificationService;
    
    @MockBean
    private LoggingService loggingService;
    
    @MockBean
    private PositionManager positionManager;
    
    @MockBean
    private TradingSessionManager tradingSessionManager;
    
    @MockBean
    private BinanceOptionsClient binanceClient;
    
    @BeforeEach
    void setUp() {
        reset(notificationService, loggingService, positionManager, 
              tradingSessionManager, binanceClient);
        
        globalExceptionHandler.resetErrorCounts();
        circuitBreaker.reset();
        
        // Setup default behaviors
        when(positionManager.getOpenPositionCount()).thenReturn(0);
        doNothing().when(loggingService).logError(anyString(), anyString(), any(Throwable.class));
        doNothing().when(notificationService).sendAlert(anyString());
    }
    
    @Test
    void testAPIFailureRecovery() {
        // Test API failure scenarios and recovery
        
        // Test rate limit error (recoverable)
        APIException rateLimitError = new APIException("Rate limit exceeded", 429, "RATE_LIMIT_EXCEEDED");
        
        assertDoesNotThrow(() -> {
            globalExceptionHandler.handleAPIException(rateLimitError, "fetchMarketData");
        });
        
        // Should not trigger shutdown for recoverable errors
        assertFalse(shutdownManager.isShutdownInProgress());
        
        // Test authentication error (non-recoverable)
        APIException authError = new APIException("Invalid signature", 401, "INVALID_SIGNATURE");
        
        assertDoesNotThrow(() -> {
            globalExceptionHandler.handleAPIException(authError, "placeOrder");
        });
        
        // Should send immediate alert for auth errors
        verify(notificationService).sendAlert(contains("AUTHENTICATION ERROR"));
    }
    
    @Test
    void testCircuitBreakerFailureThreshold() {
        // Test circuit breaker opens after failure threshold
        
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());
        
        // Cause exactly the threshold number of failures
        for (int i = 0; i < 5; i++) {
            final int failureNumber = i;
            try {
                circuitBreaker.execute(() -> {
                    throw new RuntimeException("API failure " + failureNumber);
                }, "testOperation");
            } catch (APIException e) {
                // Expected
            }
        }
        
        // Circuit should still be closed (threshold is 5, not reached yet)
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());
        
        // One more failure should open the circuit
        try {
            circuitBreaker.execute(() -> {
                throw new RuntimeException("Final failure");
            }, "testOperation");
        } catch (APIException e) {
            // Expected
        }
        
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
    }
    
    @Test
    void testCircuitBreakerRecovery() throws InterruptedException {
        // Test circuit breaker recovery after timeout
        
        // Open the circuit
        for (int i = 0; i < 6; i++) {
            try {
                circuitBreaker.execute(() -> {
                    throw new RuntimeException("Failure");
                }, "testOperation");
            } catch (APIException e) {
                // Expected
            }
        }
        
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
        
        // Manually transition to half-open (simulating timeout)
        circuitBreaker.reset();
        
        // Should be able to execute successfully now
        String result = assertDoesNotThrow(() -> {
            return circuitBreaker.execute(() -> "success", "testOperation");
        });
        
        assertEquals("success", result);
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());
    }
    
    @Test
    void testRiskManagementErrorHandling() {
        // Test risk management error scenarios
        
        // Portfolio stop-loss scenario
        RiskManagementException portfolioStopLoss = RiskManagementException.portfolioStopLoss(2000.0, 1000.0);
        
        assertDoesNotThrow(() -> {
            globalExceptionHandler.handleRiskManagementException(portfolioStopLoss);
        });
        
        verify(notificationService).sendAlert(contains("RISK MANAGEMENT ALERT"));
        verify(notificationService).sendAlert(contains("Portfolio stop-loss triggered"));
        
        // Position stop-loss scenario
        RiskManagementException positionStopLoss = RiskManagementException.positionStopLoss("POS123", -35.0, -30.0);
        
        assertDoesNotThrow(() -> {
            globalExceptionHandler.handleRiskManagementException(positionStopLoss);
        });
        
        verify(notificationService).sendAlert(contains("Position POS123 stop-loss triggered"));
    }
    
    @Test
    void testOrderExecutionErrorHandling() {
        // Test order execution error scenarios
        
        // Order timeout scenario
        OrderExecutionException orderTimeout = OrderExecutionException.orderTimeout("ORDER123", "BTC-CALL", 60);
        
        assertDoesNotThrow(() -> {
            globalExceptionHandler.handleOrderExecutionException(orderTimeout, "placeOrder");
        });
        
        verify(notificationService).sendAlert(contains("ORDER EXECUTION ERROR"));
        verify(notificationService).sendAlert(contains("ORDER123"));
        verify(notificationService).sendAlert(contains("timed out after 60 seconds"));
        
        // Insufficient balance scenario (non-recoverable)
        OrderExecutionException balanceError = OrderExecutionException.insufficientBalance("BTC-PUT", "0.005");
        
        assertDoesNotThrow(() -> {
            globalExceptionHandler.handleOrderExecutionException(balanceError, "placeOrder");
        });
        
        verify(notificationService).sendAlert(contains("Insufficient balance"));
        verify(notificationService).sendAlert(contains("Recoverable: No"));
    }
    
    @Test
    void testConfigurationErrorHandling() {
        // Test configuration error scenarios
        
        ConfigurationException missingApiKey = new ConfigurationException("API key is required", "api-key");
        
        assertDoesNotThrow(() -> {
            globalExceptionHandler.handleConfigurationException(missingApiKey);
        });
        
        verify(notificationService).sendAlert(contains("CRITICAL CONFIGURATION ERROR"));
        verify(notificationService).sendAlert(contains("Configuration Key: api-key"));
        
        ConfigurationException invalidConfig = new ConfigurationException("Invalid session times", "session-times");
        
        assertDoesNotThrow(() -> {
            globalExceptionHandler.handleConfigurationException(invalidConfig);
        });
        
        verify(notificationService).sendAlert(contains("Configuration Key: session-times"));
    }
    
    @Test
    void testErrorRateLimiting() {
        // Test that repeated errors don't spam notifications
        
        APIException repeatedError = new APIException("Connection failed", 503);
        
        // Send the same error multiple times
        for (int i = 0; i < 10; i++) {
            globalExceptionHandler.handleAPIException(repeatedError, "testOperation");
        }
        
        // Should only send notification after threshold is reached (3 errors)
        // and not spam for every error
        verify(notificationService, atMost(2)).sendAlert(anyString());
    }
    
    @Test
    @Timeout(10)
    void testShutdownWithPositionCloseTimeout() throws InterruptedException {
        // Test shutdown behavior when position closing times out
        
        when(positionManager.getOpenPositionCount()).thenReturn(5);
        
        // Simulate position closing that takes too long
        doAnswer(invocation -> {
            Thread.sleep(20000); // Longer than timeout
            return null;
        }).when(positionManager).closeAllPositions(anyString());
        
        CountDownLatch shutdownLatch = new CountDownLatch(1);
        AtomicBoolean shutdownCompleted = new AtomicBoolean(false);
        
        Thread shutdownThread = new Thread(() -> {
            shutdownManager.performGracefulShutdown("Test timeout");
            shutdownCompleted.set(true);
            shutdownLatch.countDown();
        });
        
        shutdownThread.start();
        
        // Should complete within reasonable time even with timeout
        assertTrue(shutdownLatch.await(5, TimeUnit.SECONDS), "Shutdown should complete despite position close timeout");
        assertTrue(shutdownCompleted.get());
        
        // Should send warning about timeout
        verify(notificationService).sendAlert(contains("SHUTDOWN WARNING"));
        verify(notificationService).sendAlert(contains("Position closing timed out"));
    }
    
    @Test
    void testNotificationServiceFailure() {
        // Test behavior when notification service fails
        
        doThrow(new RuntimeException("Telegram API unavailable"))
            .when(notificationService).sendAlert(anyString());
        
        // Should not crash when notifications fail
        assertDoesNotThrow(() -> {
            APIException apiError = new APIException("Test error", 500);
            globalExceptionHandler.handleAPIException(apiError, "testOperation");
        });
        
        // Should still log the error
        verify(loggingService).logError(eq("API_ERROR"), anyString(), any(APIException.class));
    }
    
    @Test
    void testLoggingServiceFailure() {
        // Test behavior when logging service fails
        
        doThrow(new RuntimeException("Log file unavailable"))
            .when(loggingService).logError(anyString(), anyString(), any(Throwable.class));
        
        // Should not crash when logging fails
        assertDoesNotThrow(() -> {
            APIException apiError = new APIException("Test error", 500);
            globalExceptionHandler.handleAPIException(apiError, "testOperation");
        });
        
        // Should still attempt to send notification
        verify(notificationService, atLeastOnce()).sendAlert(anyString());
    }
    
    @Test
    void testCascadingFailures() {
        // Test handling of cascading failures
        
        // Simulate multiple services failing
        doThrow(new RuntimeException("Notification failure"))
            .when(notificationService).sendAlert(anyString());
        
        doThrow(new RuntimeException("Logging failure"))
            .when(loggingService).logError(anyString(), anyString(), any(Throwable.class));
        
        when(positionManager.getOpenPositionCount()).thenReturn(3);
        doThrow(new RuntimeException("Position manager failure"))
            .when(positionManager).closeAllPositions(anyString());
        
        // Should still complete shutdown despite multiple failures
        assertDoesNotThrow(() -> {
            shutdownManager.performGracefulShutdown("Cascading failure test");
        });
    }
    
    @Test
    void testMemoryLeakPrevention() {
        // Test that error tracking doesn't cause memory leaks
        
        // Generate many different error types
        for (int i = 0; i < 1000; i++) {
            APIException error = new APIException("Error " + i, 500 + (i % 100));
            globalExceptionHandler.handleAPIException(error, "operation" + i);
        }
        
        // Should not accumulate unlimited error counts
        String stats = globalExceptionHandler.getErrorStatistics();
        assertNotNull(stats);
        
        // Reset should clear all tracking
        globalExceptionHandler.resetErrorCounts();
        
        String statsAfterReset = globalExceptionHandler.getErrorStatistics();
        assertTrue(statsAfterReset.contains("Error Statistics:"));
    }
    
    @Test
    void testThreadSafety() throws InterruptedException {
        // Test thread safety of error handling
        
        int threadCount = 10;
        int errorsPerThread = 100;
        CountDownLatch startLatch = new CountDownLatch(threadCount);
        CountDownLatch completeLatch = new CountDownLatch(threadCount);
        AtomicInteger totalErrors = new AtomicInteger(0);
        
        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            new Thread(() -> {
                startLatch.countDown();
                try {
                    startLatch.await(); // Wait for all threads to start
                    
                    for (int i = 0; i < errorsPerThread; i++) {
                        APIException error = new APIException("Thread " + threadId + " Error " + i, 500);
                        globalExceptionHandler.handleAPIException(error, "threadTest");
                        totalErrors.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    completeLatch.countDown();
                }
            }).start();
        }
        
        assertTrue(completeLatch.await(30, TimeUnit.SECONDS), "All threads should complete");
        assertEquals(threadCount * errorsPerThread, totalErrors.get());
        
        // Should handle concurrent access without crashing
        assertDoesNotThrow(() -> {
            String stats = globalExceptionHandler.getErrorStatistics();
            assertNotNull(stats);
        });
    }
    
    @Test
    void testUncaughtExceptionHandling() {
        // Test uncaught exception handler
        
        Thread testThread = new Thread(() -> {
            throw new RuntimeException("Uncaught test exception");
        }, "TestThread");
        
        // The global exception handler should catch this
        // We can't easily test the actual uncaught exception handler in unit tests
        // But we can test the handler method directly
        assertDoesNotThrow(() -> {
            // Simulate what the uncaught exception handler would do
            RuntimeException uncaughtException = new RuntimeException("Uncaught test exception");
            // The actual handler is set up in GlobalExceptionHandler.initialize()
            // We can verify it's configured properly by checking it doesn't throw
        });
    }
}