package com.trading.bot;

import com.trading.bot.exception.ConfigurationException;
import com.trading.bot.exception.TradingBotException;
import com.trading.bot.service.LoggingService;
import com.trading.bot.service.NotificationService;
import com.trading.bot.service.PositionManager;
import com.trading.bot.service.TradingSessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.annotation.PreDestroy;
import java.util.concurrent.CountDownLatch;

@SpringBootApplication
@EnableScheduling
public class BtcOptionsStraddleBotApplication implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(BtcOptionsStraddleBotApplication.class);
    
    @Autowired
    private TradingSessionManager tradingSessionManager;
    
    @Autowired
    private PositionManager positionManager;
    
    @Autowired
    private NotificationService notificationService;
    
    @Autowired
    private LoggingService loggingService;
    
    private static ConfigurableApplicationContext applicationContext;
    private final CountDownLatch shutdownLatch = new CountDownLatch(1);
    private volatile boolean shutdownRequested = false;

    public static void main(String[] args) {
        // Set system properties for better logging and error handling
        System.setProperty("spring.main.banner-mode", "console");
        System.setProperty("logging.level.com.trading.bot", "INFO");
        
        try {
            logger.info("🚀 Starting BTC Options Straddle Bot Application...");
            
            applicationContext = SpringApplication.run(BtcOptionsStraddleBotApplication.class, args);
            
            // Add shutdown hook for graceful shutdown
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutdown hook triggered - initiating graceful shutdown");
                if (applicationContext != null) {
                    applicationContext.close();
                }
            }));
            
        } catch (Exception e) {
            logger.error("Failed to start application: {}", e.getMessage(), e);
            System.exit(1);
        }
    }

    @Override
    public void run(String... args) throws Exception {
        try {
            logger.info("Application started successfully - initializing trading bot components");
            
            // Log application startup
            loggingService.logApplicationEvent("APPLICATION_STARTED", "BTC Options Straddle Bot started successfully");
            
            // Send startup notification
            notificationService.sendNotification(
                "🤖 BTC OPTIONS STRADDLE BOT STARTED\n" +
                "Application initialized successfully\n" +
                "Waiting for trading session to begin..."
            );
            
            // Start the trading session
            startTradingSession();
            
            // Wait for shutdown signal
            waitForShutdown();
            
        } catch (Exception e) {
            if (e instanceof ConfigurationException) {
                ConfigurationException configEx = (ConfigurationException) e;
                logger.error("Configuration error during startup: {}", configEx.getFormattedMessage());
                loggingService.logError("CONFIGURATION_ERROR", configEx.getFormattedMessage(), configEx);
                notificationService.sendAlert("❌ CONFIGURATION ERROR\n" + configEx.getMessage());
                throw configEx;
            } else if (e instanceof TradingBotException) {
                TradingBotException tradingEx = (TradingBotException) e;
                logger.error("Trading bot error during startup: {}", tradingEx.getFormattedMessage());
                loggingService.logError("STARTUP_ERROR", tradingEx.getFormattedMessage(), tradingEx);
                notificationService.sendAlert("❌ STARTUP ERROR\n" + tradingEx.getMessage());
                throw tradingEx;
            } else {
                logger.error("Unexpected error during startup: {}", e.getMessage(), e);
                loggingService.logError("UNEXPECTED_STARTUP_ERROR", e.getMessage(), e);
                notificationService.sendAlert("❌ UNEXPECTED STARTUP ERROR\n" + e.getMessage());
                throw new TradingBotException("Unexpected startup error", e);
            }
        }
    }
    
    private void startTradingSession() {
        try {
            logger.info("Starting trading session...");
            tradingSessionManager.startTradingSession();
            
        } catch (Exception e) {
            if (e instanceof TradingBotException) {
                TradingBotException tradingEx = (TradingBotException) e;
                logger.error("Failed to start trading session: {}", tradingEx.getFormattedMessage());
                loggingService.logError("SESSION_START_ERROR", tradingEx.getFormattedMessage(), tradingEx);
                notificationService.sendAlert("❌ SESSION START ERROR\n" + tradingEx.getMessage());
                
                if (!tradingEx.isRecoverable()) {
                    logger.error("Non-recoverable error - shutting down application");
                    initiateShutdown("Non-recoverable session start error");
                }
            } else {
                logger.error("Unexpected error starting trading session: {}", e.getMessage(), e);
                loggingService.logError("UNEXPECTED_SESSION_ERROR", e.getMessage(), e);
                notificationService.sendAlert("❌ UNEXPECTED SESSION ERROR\n" + e.getMessage());
                initiateShutdown("Unexpected session start error");
            }
        }
    }
    
    private void waitForShutdown() {
        try {
            logger.info("Application running - waiting for shutdown signal...");
            shutdownLatch.await();
            logger.info("Shutdown signal received - application will terminate");
            
        } catch (InterruptedException e) {
            logger.warn("Shutdown wait interrupted");
            Thread.currentThread().interrupt();
        }
    }
    
    public void initiateShutdown(String reason) {
        if (shutdownRequested) {
            return; // Already shutting down
        }
        
        shutdownRequested = true;
        logger.info("Initiating application shutdown - Reason: {}", reason);
        
        try {
            // Log shutdown initiation
            loggingService.logApplicationEvent("SHUTDOWN_INITIATED", "Reason: " + reason);
            
            // End trading session gracefully
            if (tradingSessionManager.isSessionActive()) {
                logger.info("Ending active trading session...");
                tradingSessionManager.endSession(reason);
            }
            
            // Send shutdown notification
            notificationService.sendNotification(
                "🛑 BTC OPTIONS STRADDLE BOT SHUTTING DOWN\n" +
                "Reason: " + reason + "\n" +
                "All positions have been handled appropriately"
            );
            
            // Log final application state
            loggingService.logApplicationEvent("APPLICATION_SHUTDOWN", "Shutdown completed successfully");
            
        } catch (Exception e) {
            logger.error("Error during shutdown process: {}", e.getMessage(), e);
            loggingService.logError("SHUTDOWN_ERROR", e.getMessage(), e);
            
        } finally {
            // Release the shutdown latch
            shutdownLatch.countDown();
        }
    }
    
    @PreDestroy
    public void onDestroy() {
        logger.info("Application context is being destroyed - performing final cleanup");
        
        try {
            // Ensure all positions are closed
            if (positionManager.getOpenPositionCount() > 0) {
                logger.warn("Found {} open positions during shutdown - closing them", 
                           positionManager.getOpenPositionCount());
                positionManager.closeAllPositions("Application shutdown");
            }
            
            // Final logging
            loggingService.logApplicationEvent("APPLICATION_DESTROYED", "Final cleanup completed");
            
        } catch (Exception e) {
            logger.error("Error during final cleanup: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Get the Spring application context (for testing purposes)
     */
    public static ConfigurableApplicationContext getApplicationContext() {
        return applicationContext;
    }
    
    /**
     * Check if shutdown has been requested
     */
    public boolean isShutdownRequested() {
        return shutdownRequested;
    }
}