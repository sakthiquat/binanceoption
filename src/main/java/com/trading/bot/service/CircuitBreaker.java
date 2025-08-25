package com.trading.bot.service;

import com.trading.bot.exception.APIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Circuit breaker implementation for API calls to prevent cascading failures.
 * Implements the circuit breaker pattern with configurable thresholds and timeouts.
 */
@Component
public class CircuitBreaker {
    
    private static final Logger logger = LoggerFactory.getLogger(CircuitBreaker.class);
    
    public enum State {
        CLOSED,    // Normal operation
        OPEN,      // Circuit is open, calls are failing fast
        HALF_OPEN  // Testing if service has recovered
    }
    
    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicInteger successCount = new AtomicInteger(0);
    private volatile LocalDateTime lastFailureTime;
    private volatile LocalDateTime lastStateChange;
    
    // Configuration
    private final int failureThreshold = 5;           // Open circuit after 5 failures
    private final int successThreshold = 3;           // Close circuit after 3 successes in half-open
    private final long timeoutMinutes = 2;            // Wait 2 minutes before trying half-open
    private final long resetTimeoutMinutes = 10;      // Reset failure count after 10 minutes
    
    /**
     * Execute a supplier with circuit breaker protection
     */
    public <T> T execute(Supplier<T> operation, String operationName) throws APIException {
        State currentState = state.get();
        
        switch (currentState) {
            case OPEN:
                if (shouldAttemptReset()) {
                    return executeInHalfOpenState(operation, operationName);
                } else {
                    throw new APIException(
                        String.format("Circuit breaker is OPEN for operation: %s. Last failure: %s", 
                                    operationName, lastFailureTime),
                        503,
                        "CIRCUIT_BREAKER_OPEN"
                    );
                }
                
            case HALF_OPEN:
                return executeInHalfOpenState(operation, operationName);
                
            case CLOSED:
            default:
                return executeInClosedState(operation, operationName);
        }
    }
    
    private <T> T executeInClosedState(Supplier<T> operation, String operationName) throws APIException {
        try {
            T result = operation.get();
            onSuccess(operationName);
            return result;
        } catch (Exception e) {
            onFailure(operationName, e);
            throw convertToAPIException(e, operationName);
        }
    }
    
    private <T> T executeInHalfOpenState(Supplier<T> operation, String operationName) throws APIException {
        try {
            T result = operation.get();
            onSuccessInHalfOpen(operationName);
            return result;
        } catch (Exception e) {
            onFailureInHalfOpen(operationName, e);
            throw convertToAPIException(e, operationName);
        }
    }
    
    private void onSuccess(String operationName) {
        // Reset failure count if enough time has passed
        if (shouldResetFailureCount()) {
            failureCount.set(0);
            logger.debug("Reset failure count for circuit breaker due to timeout");
        }
        
        logger.trace("Circuit breaker success for operation: {}", operationName);
    }
    
    private void onFailure(String operationName, Exception e) {
        lastFailureTime = LocalDateTime.now();
        int failures = failureCount.incrementAndGet();
        
        logger.warn("Circuit breaker failure #{} for operation: {} - {}", 
                   failures, operationName, e.getMessage());
        
        if (failures >= failureThreshold) {
            openCircuit(operationName);
        }
    }
    
    private void onSuccessInHalfOpen(String operationName) {
        int successes = successCount.incrementAndGet();
        logger.info("Circuit breaker half-open success #{} for operation: {}", successes, operationName);
        
        if (successes >= successThreshold) {
            closeCircuit(operationName);
        }
    }
    
    private void onFailureInHalfOpen(String operationName, Exception e) {
        logger.warn("Circuit breaker half-open failure for operation: {} - {}", operationName, e.getMessage());
        openCircuit(operationName);
    }
    
    private void openCircuit(String operationName) {
        state.set(State.OPEN);
        lastStateChange = LocalDateTime.now();
        successCount.set(0);
        
        logger.error("Circuit breaker OPENED for operation: {} after {} failures", 
                    operationName, failureCount.get());
    }
    
    private void closeCircuit(String operationName) {
        state.set(State.CLOSED);
        lastStateChange = LocalDateTime.now();
        failureCount.set(0);
        successCount.set(0);
        
        logger.info("Circuit breaker CLOSED for operation: {} after {} successes", 
                   operationName, successThreshold);
    }
    
    private boolean shouldAttemptReset() {
        if (lastStateChange == null) {
            return true;
        }
        
        long minutesSinceOpen = ChronoUnit.MINUTES.between(lastStateChange, LocalDateTime.now());
        boolean shouldReset = minutesSinceOpen >= timeoutMinutes;
        
        if (shouldReset) {
            state.compareAndSet(State.OPEN, State.HALF_OPEN);
            lastStateChange = LocalDateTime.now();
            successCount.set(0);
            logger.info("Circuit breaker transitioning to HALF_OPEN state after {} minutes", minutesSinceOpen);
        }
        
        return shouldReset;
    }
    
    private boolean shouldResetFailureCount() {
        if (lastFailureTime == null) {
            return false;
        }
        
        long minutesSinceLastFailure = ChronoUnit.MINUTES.between(lastFailureTime, LocalDateTime.now());
        return minutesSinceLastFailure >= resetTimeoutMinutes;
    }
    
    private APIException convertToAPIException(Exception e, String operationName) {
        if (e instanceof APIException) {
            return (APIException) e;
        }
        
        return new APIException(
            String.format("Operation '%s' failed: %s", operationName, e.getMessage()),
            e,
            -1,
            "OPERATION_FAILED"
        );
    }
    
    /**
     * Get current circuit breaker state
     */
    public State getState() {
        return state.get();
    }
    
    /**
     * Get current failure count
     */
    public int getFailureCount() {
        return failureCount.get();
    }
    
    /**
     * Get current success count (relevant in half-open state)
     */
    public int getSuccessCount() {
        return successCount.get();
    }
    
    /**
     * Get last failure time
     */
    public LocalDateTime getLastFailureTime() {
        return lastFailureTime;
    }
    
    /**
     * Manually reset the circuit breaker (for testing or administrative purposes)
     */
    public void reset() {
        state.set(State.CLOSED);
        failureCount.set(0);
        successCount.set(0);
        lastFailureTime = null;
        lastStateChange = LocalDateTime.now();
        
        logger.info("Circuit breaker manually reset to CLOSED state");
    }
    
    /**
     * Get circuit breaker status information
     */
    public String getStatusInfo() {
        return String.format(
            "Circuit Breaker Status: State=%s, Failures=%d, Successes=%d, LastFailure=%s",
            state.get(),
            failureCount.get(),
            successCount.get(),
            lastFailureTime != null ? lastFailureTime.toString() : "None"
        );
    }
}