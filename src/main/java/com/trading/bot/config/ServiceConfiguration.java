package com.trading.bot.config;

import com.trading.bot.exception.GlobalExceptionHandler;
import com.trading.bot.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import javax.annotation.PostConstruct;
import javax.validation.Validator;
import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class ServiceConfiguration {
    
    private static final Logger logger = LoggerFactory.getLogger(ServiceConfiguration.class);
    
    @Autowired
    private PositionManager positionManager;
    
    @Autowired
    private RiskManager riskManager;
    
    @Autowired
    private GlobalExceptionHandler globalExceptionHandler;
    
    @Autowired
    private ShutdownManager shutdownManager;
    
    @Autowired
    private TradingSessionManager tradingSessionManager;
    
    @Autowired
    private CircuitBreaker circuitBreaker;
    
    @Autowired
    private NotificationService notificationService;
    
    @Autowired
    private LoggingService loggingService;
    
    @Autowired
    private BinanceOptionsClient binanceClient;
    
    @PostConstruct
    @DependsOn({"tradingConfig", "globalExceptionHandler"})
    public void configureServices() {
        logger.info("🔧 Configuring trading bot services and dependencies...");
        
        try {
            // Configure service dependencies and cross-references
            configureServiceDependencies();
            
            // Configure error handling integration
            configureErrorHandling();
            
            // Configure monitoring and health checks
            configureMonitoring();
            
            // Validate service configuration
            validateServiceConfiguration();
            
            logger.info("✅ All services configured successfully");
            
        } catch (Exception e) {
            logger.error("❌ Error configuring services: {}", e.getMessage(), e);
            globalExceptionHandler.handleTradingBotException(
                new com.trading.bot.exception.ConfigurationException("Service configuration failed", e),
                "SERVICE_CONFIGURATION"
            );
            throw e;
        }
    }
    
    private void configureServiceDependencies() {
        logger.info("Configuring service dependencies...");
        
        // Resolve circular dependency by setting RiskManager in PositionManager
        positionManager.setRiskManager(riskManager);
        
        // Configure circuit breaker integration with API client
        // This would be done in the actual service implementations
        
        logger.info("Service dependencies configured");
    }
    
    private void configureErrorHandling() {
        logger.info("Configuring error handling integration...");
        
        // Set up uncaught exception handler
        Thread.setDefaultUncaughtExceptionHandler((thread, exception) -> {
            logger.error("Uncaught exception in thread '{}': {}", thread.getName(), exception.getMessage(), exception);
            globalExceptionHandler.handleTradingBotException(
                new com.trading.bot.exception.TradingBotException("Uncaught exception in thread: " + thread.getName(), exception),
                "UNCAUGHT_EXCEPTION"
            );
        });
        
        logger.info("Error handling integration configured");
    }
    
    private void configureMonitoring() {
        logger.info("Configuring monitoring and health checks...");
        
        // Configure JVM shutdown hooks
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("JVM shutdown hook triggered");
            if (!shutdownManager.isShutdownInProgress()) {
                shutdownManager.performGracefulShutdown("JVM shutdown");
            }
        }, "ShutdownHook"));
        
        logger.info("Monitoring and health checks configured");
    }
    
    private void validateServiceConfiguration() {
        logger.info("Validating service configuration...");
        
        // Validate that all required services are properly initialized
        if (positionManager == null) {
            throw new RuntimeException("PositionManager not initialized");
        }
        
        if (riskManager == null) {
            throw new RuntimeException("RiskManager not initialized");
        }
        
        if (globalExceptionHandler == null) {
            throw new RuntimeException("GlobalExceptionHandler not initialized");
        }
        
        if (shutdownManager == null) {
            throw new RuntimeException("ShutdownManager not initialized");
        }
        
        if (tradingSessionManager == null) {
            throw new RuntimeException("TradingSessionManager not initialized");
        }
        
        if (circuitBreaker == null) {
            throw new RuntimeException("CircuitBreaker not initialized");
        }
        
        if (notificationService == null) {
            throw new RuntimeException("NotificationService not initialized");
        }
        
        if (loggingService == null) {
            throw new RuntimeException("LoggingService not initialized");
        }
        
        if (binanceClient == null) {
            throw new RuntimeException("BinanceOptionsClient not initialized");
        }
        
        logger.info("Service configuration validation completed");
    }
    
    /**
     * Bean for validation
     */
    @Bean
    public Validator validator() {
        return new LocalValidatorFactoryBean();
    }
    
    /**
     * Task executor for async operations
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("TradingBot-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        
        logger.info("Task executor configured: core={}, max={}, queue={}", 
                   executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());
        
        return executor;
    }
    
    /**
     * Health check service for monitoring application state
     */
    @Bean
    public HealthCheckService healthCheckService() {
        return new HealthCheckService();
    }
    
    /**
     * Simple health check service implementation
     */
    public static class HealthCheckService {
        private static final Logger logger = LoggerFactory.getLogger(HealthCheckService.class);
        
        @Autowired
        private PositionManager positionManager;
        
        @Autowired
        private TradingSessionManager tradingSessionManager;
        
        @Autowired
        private CircuitBreaker circuitBreaker;
        
        public boolean isHealthy() {
            try {
                // Check if critical services are responsive
                boolean positionManagerHealthy = positionManager != null;
                boolean sessionManagerHealthy = tradingSessionManager != null;
                boolean circuitBreakerHealthy = circuitBreaker != null && 
                    circuitBreaker.getState() != CircuitBreaker.State.OPEN;
                
                boolean healthy = positionManagerHealthy && sessionManagerHealthy && circuitBreakerHealthy;
                
                if (!healthy) {
                    logger.warn("Health check failed: positionManager={}, sessionManager={}, circuitBreaker={}", 
                               positionManagerHealthy, sessionManagerHealthy, circuitBreakerHealthy);
                }
                
                return healthy;
                
            } catch (Exception e) {
                logger.error("Health check error: {}", e.getMessage(), e);
                return false;
            }
        }
        
        public String getHealthStatus() {
            if (isHealthy()) {
                return String.format("HEALTHY - Session: %s, Positions: %d, Circuit: %s",
                    tradingSessionManager != null && tradingSessionManager.isSessionActive() ? "ACTIVE" : "INACTIVE",
                    positionManager != null ? positionManager.getOpenPositionCount() : 0,
                    circuitBreaker != null ? circuitBreaker.getState() : "UNKNOWN");
            } else {
                return "UNHEALTHY - Check logs for details";
            }
        }
    }
}