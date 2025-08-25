package com.trading.bot.service;

import com.trading.bot.exception.TradingBotException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages graceful shutdown of the trading bot application.
 * Ensures all positions are properly closed and resources are cleaned up.
 */
@Service
public class ShutdownManager implements ApplicationListener<ContextClosedEvent> {
    
    private static final Logger logger = LoggerFactory.getLogger(ShutdownManager.class);
    
    @Autowired
    private TradingSessionManager tradingSessionManager;
    
    @Autowired
    private PositionManager positionManager;
    
    @Autowired
    private CycleScheduler cycleScheduler;
    
    @Autowired
    private NotificationService notificationService;
    
    @Autowired
    private LoggingService loggingService;
    
    private final AtomicBoolean shutdownInProgress = new AtomicBoolean(false);
    private final CountDownLatch shutdownComplete = new CountDownLatch(1);
    
    // Shutdown configuration
    private final long maxShutdownTimeSeconds = 30;
    private final long positionCloseTimeoutSeconds = 15;
    
    /**
     * Handle application context closed event
     */
    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        logger.info("Application context closed event received - initiating graceful shutdown");
        performGracefulShutdown("Application context closed");
    }
    
    /**
     * Perform graceful shutdown with position cleanup
     */
    public void performGracefulShutdown(String reason) {
        if (!shutdownInProgress.compareAndSet(false, true)) {
            logger.info("Shutdown already in progress - waiting for completion");
            waitForShutdownCompletion();
            return;
        }
        
        logger.info("🛑 INITIATING GRACEFUL SHUTDOWN - Reason: {}", reason);
        
        try {
            // Log shutdown initiation
            loggingService.logApplicationEvent("GRACEFUL_SHUTDOWN_STARTED", "Reason: " + reason);
            
            // Step 1: Stop accepting new trading cycles
            stopTradingOperations(reason);
            
            // Step 2: Close all open positions
            closeAllPositions(reason);
            
            // Step 3: Clean up resources
            cleanupResources();
            
            // Step 4: Send final notifications
            sendShutdownNotifications(reason);
            
            logger.info("✅ Graceful shutdown completed successfully");
            loggingService.logApplicationEvent("GRACEFUL_SHUTDOWN_COMPLETED", "Shutdown successful");
            
        } catch (Exception e) {
            logger.error("Error during graceful shutdown: {}", e.getMessage(), e);
            loggingService.logError("SHUTDOWN_ERROR", "Error during graceful shutdown: " + e.getMessage(), e);
            
            // Send error notification
            notificationService.sendAlert(
                "❌ SHUTDOWN ERROR\n" +
                "Error during graceful shutdown: " + e.getMessage() + "\n" +
                "Manual intervention may be required"
            );
            
        } finally {
            shutdownComplete.countDown();
        }
    }
    
    /**
     * Stop all trading operations
     */
    private void stopTradingOperations(String reason) {
        logger.info("Stopping trading operations...");
        
        try {
            // Stop cycle scheduling first
            if (cycleScheduler != null) {
                logger.info("Stopping cycle scheduler...");
                cycleScheduler.stopCycleScheduling();
            }
            
            // End trading session
            if (tradingSessionManager != null && tradingSessionManager.isSessionActive()) {
                logger.info("Ending active trading session...");
                tradingSessionManager.endSession(reason);
            }
            
            logger.info("Trading operations stopped successfully");
            
        } catch (Exception e) {
            logger.error("Error stopping trading operations: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to stop trading operations", e);
        }
    }
    
    /**
     * Close all open positions with timeout
     */
    private void closeAllPositions(String reason) {
        if (positionManager == null) {
            logger.warn("Position manager not available - cannot close positions");
            return;
        }
        
        int openPositions = positionManager.getOpenPositionCount();
        if (openPositions == 0) {
            logger.info("No open positions to close");
            return;
        }
        
        logger.info("Closing {} open positions due to shutdown...", openPositions);
        
        try {
            // Create a separate thread for position closing with timeout
            CountDownLatch positionCloseLatch = new CountDownLatch(1);
            AtomicBoolean positionsClosedSuccessfully = new AtomicBoolean(false);
            
            Thread positionCloseThread = new Thread(() -> {
                try {
                    positionManager.closeAllPositions(reason);
                    positionsClosedSuccessfully.set(true);
                    logger.info("All positions closed successfully");
                } catch (Exception e) {
                    logger.error("Error closing positions: {}", e.getMessage(), e);
                } finally {
                    positionCloseLatch.countDown();
                }
            }, "PositionCloseThread");
            
            positionCloseThread.start();
            
            // Wait for positions to close with timeout
            boolean completed = positionCloseLatch.await(positionCloseTimeoutSeconds, TimeUnit.SECONDS);
            
            if (!completed) {
                logger.error("Position closing timed out after {} seconds", positionCloseTimeoutSeconds);
                positionCloseThread.interrupt();
                
                // Send alert about unclosed positions
                notificationService.sendAlert(
                    "⚠️ SHUTDOWN WARNING\n" +
                    "Position closing timed out\n" +
                    "Some positions may still be open\n" +
                    "Manual intervention required"
                );
            } else if (!positionsClosedSuccessfully.get()) {
                logger.error("Position closing completed but with errors");
                notificationService.sendAlert(
                    "⚠️ SHUTDOWN WARNING\n" +
                    "Errors occurred while closing positions\n" +
                    "Check logs and verify position status manually"
                );
            }
            
        } catch (InterruptedException e) {
            logger.error("Position closing interrupted: {}", e.getMessage());
            Thread.currentThread().interrupt();
            throw new RuntimeException("Position closing interrupted", e);
        }
    }
    
    /**
     * Clean up application resources
     */
    private void cleanupResources() {
        logger.info("Cleaning up application resources...");
        
        try {
            // Additional cleanup can be added here
            // For example: closing database connections, file handles, etc.
            
            logger.info("Resource cleanup completed");
            
        } catch (Exception e) {
            logger.error("Error during resource cleanup: {}", e.getMessage(), e);
            // Don't throw exception here as it's not critical for shutdown
        }
    }
    
    /**
     * Send shutdown notifications
     */
    private void sendShutdownNotifications(String reason) {
        try {
            String shutdownMessage = String.format(
                "🛑 BTC OPTIONS STRADDLE BOT SHUTDOWN\n" +
                "Reason: %s\n" +
                "Time: %s\n" +
                "Status: Graceful shutdown completed\n" +
                "All positions have been handled appropriately",
                reason,
                java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            );
            
            notificationService.sendNotification(shutdownMessage);
            
        } catch (Exception e) {
            logger.error("Error sending shutdown notifications: {}", e.getMessage(), e);
            // Don't throw exception as notification failure shouldn't prevent shutdown
        }
    }
    
    /**
     * Wait for shutdown completion
     */
    private void waitForShutdownCompletion() {
        try {
            boolean completed = shutdownComplete.await(maxShutdownTimeSeconds, TimeUnit.SECONDS);
            if (!completed) {
                logger.warn("Shutdown completion wait timed out after {} seconds", maxShutdownTimeSeconds);
            }
        } catch (InterruptedException e) {
            logger.warn("Shutdown completion wait interrupted");
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Emergency shutdown for critical errors
     */
    public void emergencyShutdown(String reason, TradingBotException cause) {
        logger.error("🚨 EMERGENCY SHUTDOWN INITIATED - Reason: {}", reason);
        loggingService.logError("EMERGENCY_SHUTDOWN", "Reason: " + reason, cause);
        
        try {
            // Send immediate alert
            notificationService.sendAlert(
                "🚨 EMERGENCY SHUTDOWN\n" +
                "Reason: " + reason + "\n" +
                "Error: " + (cause != null ? cause.getMessage() : "Unknown") + "\n" +
                "Immediate action required"
            );
            
            // Attempt rapid position closure
            if (positionManager != null && positionManager.getOpenPositionCount() > 0) {
                logger.error("Attempting emergency position closure...");
                try {
                    positionManager.closeAllPositions("Emergency shutdown: " + reason);
                } catch (Exception e) {
                    logger.error("Emergency position closure failed: {}", e.getMessage(), e);
                }
            }
            
        } catch (Exception e) {
            logger.error("Error during emergency shutdown: {}", e.getMessage(), e);
        }
        
        // Force application exit
        logger.error("Forcing application exit due to emergency shutdown");
        System.exit(1);
    }
    
    /**
     * Check if shutdown is in progress
     */
    public boolean isShutdownInProgress() {
        return shutdownInProgress.get();
    }
    
    /**
     * PreDestroy method for Spring container shutdown
     */
    @PreDestroy
    public void preDestroy() {
        logger.info("PreDestroy method called - ensuring graceful shutdown");
        performGracefulShutdown("Spring container shutdown");
    }
}