package com.trading.bot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

@Service
public class RetryHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(RetryHandler.class);
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_DELAY = 1000; // 1 second
    
    public <T> T executeWithRetry(Supplier<T> operation, String operationName) throws Exception {
        Exception lastException = null;
        
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                logger.debug("Executing {} - Attempt {}/{}", operationName, attempt, MAX_RETRIES);
                return operation.get();
            } catch (Exception e) {
                lastException = e;
                logger.warn("Attempt {}/{} failed for {}: {}", attempt, MAX_RETRIES, operationName, e.getMessage());
                
                if (attempt < MAX_RETRIES) {
                    long delay = INITIAL_DELAY * (1L << (attempt - 1)); // Exponential backoff
                    logger.info("Retrying {} in {} ms", operationName, delay);
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Retry interrupted", ie);
                    }
                }
            }
        }
        
        logger.error("All {} attempts failed for {}", MAX_RETRIES, operationName);
        throw new RuntimeException("Operation failed after " + MAX_RETRIES + " attempts", lastException);
    }
    
    public void executeWithRetryVoid(Runnable operation, String operationName) throws Exception {
        executeWithRetry(() -> {
            operation.run();
            return null;
        }, operationName);
    }
}