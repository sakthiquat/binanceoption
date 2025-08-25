package com.trading.bot;

import com.trading.bot.config.TradingConfig;
import com.trading.bot.exception.ConfigurationException;
import com.trading.bot.exception.GlobalExceptionHandler;
import com.trading.bot.exception.TradingBotException;
import com.trading.bot.service.*;
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
    
    @Autowired
    private GlobalExceptionHandler globalExceptionHandler;
    
    @Autowired
    private ShutdownManager shutdownManager;
    
    @Autowired
    private TradingConfig tradingConfig;
    
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
            
            // Validate configuration
            validateConfiguration();
            
            // Log application startup
            loggingService.logApplicationEvent("APPLICATION_STARTED", "BTC Options Straddle Bot started successfully");
            
            // Send startup notification with configuration summary
            sendStartupNotification();
            
            // Start the trading session
            startTradingSession();
            
            // Wait for shutdown signal
            waitForShutdown();
            
        } catch (Exception e) {
            handleStartupException(e);
        }
    }
    
    private void validateConfiguration() throws ConfigurationException {
        logger.info("Validating application configuration...");
        
        try {
            // Validate required configuration parameters
            if (tradingConfig.getApiKey() == null || tradingConfig.getApiKey().trim().isEmpty()) {
                throw new ConfigurationException("API key is required", "api-key");
            }
            
            if (tradingConfig.getSecretKey() == null || tradingConfig.getSecretKey().trim().isEmpty()) {
                throw new ConfigurationException("Secret key is required", "secret-key");
            }
            
            if (tradingConfig.getSessionStartTime() == null) {
                throw new ConfigurationException("Session start time is required", "session-start-time");
            }
            
            if (tradingConfig.getSessionEndTime() == null) {
                throw new ConfigurationException("Session end time is required", "session-end-time");
            }
            
            // Validate session times
            if (!tradingConfig.getSessionStartTime().isBefore(tradingConfig.getSessionEndTime())) {
                throw new ConfigurationException("Session start time must be before end time", "session-times");
            }
            
            // Validate numeric parameters
            if (tradingConfig.getPositionQuantity().compareTo(java.math.BigDecimal.ZERO) <= 0) {
                throw new ConfigurationException("Position quantity must be positive", "position-quantity");
            }
            
            if (tradingConfig.getCycleIntervalMinutes() <= 0) {
                throw new ConfigurationException("Cycle interval must be positive", "cycle-interval-minutes");
            }
            
            if (tradingConfig.getNumberOfCycles() <= 0) {
                throw new ConfigurationException("Number of cycles must be positive", "number-of-cycles");
            }
            
            logger.info("Configuration validation completed successfully");
            
        } catch (ConfigurationException e) {
            globalExceptionHandler.handleConfigurationException(e);
            throw e;
        }
    }
    
    private void sendStartupNotification() {
        try {
            String startupMessage = String.format(
                "🤖 BTC OPTIONS STRADDLE BOT STARTED\n" +
                "Session: %s - %s\n" +
                "Cycles: %d (every %d minutes)\n" +
                "Position Size: %s BTC\n" +
                "Strike Distance: %d\n" +
                "Waiting for trading session to begin...",
                tradingConfig.getSessionStartTime(),
                tradingConfig.getSessionEndTime(),
                tradingConfig.getNumberOfCycles(),
                tradingConfig.getCycleIntervalMinutes(),
                tradingConfig.getPositionQuantity(),
                tradingConfig.getStrikeDistance()
            );
            
            notificationService.sendNotification(startupMessage);
            
        } catch (Exception e) {
            logger.warn("Failed to send startup notification: {}", e.getMessage());
        }
    }
    
    private void handleStartupException(Exception e) throws Exception {
        if (e instanceof ConfigurationException) {
            ConfigurationException configEx = (ConfigurationException) e;
            logger.error("Configuration error during startup: {}", configEx.getFormattedMessage());
            // Exception already handled by globalExceptionHandler in validateConfiguration
            throw configEx;
        } else if (e instanceof TradingBotException) {
            TradingBotException tradingEx = (TradingBotException) e;
            globalExceptionHandler.handleTradingBotException(tradingEx, "STARTUP");
            throw tradingEx;
        } else {
            logger.error("Unexpected error during startup: {}", e.getMessage(), e);
            TradingBotException wrappedException = new TradingBotException("Unexpected startup error", e);
            globalExceptionHandler.handleTradingBotException(wrappedException, "STARTUP");
            throw wrappedException;
        }
    }
    
    private void startTradingSession() {
        try {
            logger.info("Starting trading session...");
            tradingSessionManager.startTradingSession();
            
        } catch (Exception e) {
            if (e instanceof TradingBotException) {
                TradingBotException tradingEx = (TradingBotException) e;
                globalExceptionHandler.handleTradingBotException(tradingEx, "SESSION_START");
                
                if (!tradingEx.isRecoverable()) {
                    logger.error("Non-recoverable error - shutting down application");
                    shutdownManager.performGracefulShutdown("Non-recoverable session start error");
                }
            } else {
                logger.error("Unexpected error starting trading session: {}", e.getMessage(), e);
                TradingBotException wrappedException = new TradingBotException("Unexpected session start error", e);
                globalExceptionHandler.handleTradingBotException(wrappedException, "SESSION_START");
                shutdownManager.performGracefulShutdown("Unexpected session start error");
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
        
        // Delegate to shutdown manager for proper handling
        shutdownManager.performGracefulShutdown(reason);
        
        // Release the shutdown latch
        shutdownLatch.countDown();
    }
    
    @PreDestroy
    public void onDestroy() {
        logger.info("Application context is being destroyed - performing final cleanup");
        
        try {
            // Shutdown manager handles the cleanup
            if (!shutdownManager.isShutdownInProgress()) {
                shutdownManager.performGracefulShutdown("Application context destroyed");
            }
            
            // Final logging
            loggingService.logApplicationEvent("APPLICATION_DESTROYED", "Final cleanup completed");
            
        } catch (Exception e) {
            logger.error("Error during final cleanup: {}", e.getMessage(), e);
            globalExceptionHandler.handleTradingBotException(
                new TradingBotException("Final cleanup error", e), "DESTROY");
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