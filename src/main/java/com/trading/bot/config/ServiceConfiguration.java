package com.trading.bot.config;

import com.trading.bot.exception.GlobalExceptionHandler;
import com.trading.bot.service.PositionManager;
import com.trading.bot.service.RiskManager;
import com.trading.bot.service.ShutdownManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import javax.annotation.PostConstruct;
import javax.validation.Validator;

@Configuration
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
    
    @PostConstruct
    public void configureServices() {
        logger.info("Configuring trading bot services...");
        
        try {
            // Resolve circular dependency by setting RiskManager in PositionManager
            positionManager.setRiskManager(riskManager);
            
            // Configure global exception handler
            logger.info("Global exception handler configured");
            
            // Configure shutdown manager
            logger.info("Shutdown manager configured");
            
            logger.info("All services configured successfully");
            
        } catch (Exception e) {
            logger.error("Error configuring services: {}", e.getMessage(), e);
            globalExceptionHandler.handleTradingBotException(
                new com.trading.bot.exception.ConfigurationException("Service configuration failed", e),
                "serviceConfiguration"
            );
            throw e;
        }
    }
    
    /**
     * Bean for validation
     */
    @Bean
    public Validator validator() {
        return new LocalValidatorFactoryBean();
    }
}