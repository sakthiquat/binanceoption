package com.trading.bot.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.validation.ConstraintViolation;
import javax.validation.Validator;
import java.util.Set;

@Component
public class ConfigLoader {
    
    private static final Logger logger = LoggerFactory.getLogger(ConfigLoader.class);
    
    @Autowired
    private TradingConfig tradingConfig;
    
    @Autowired
    private Validator validator;
    
    @PostConstruct
    public void validateConfiguration() {
        logger.info("Validating trading configuration...");
        
        Set<ConstraintViolation<TradingConfig>> violations = validator.validate(tradingConfig);
        
        if (!violations.isEmpty()) {
            StringBuilder errorMessage = new StringBuilder("Configuration validation failed:\n");
            for (ConstraintViolation<TradingConfig> violation : violations) {
                errorMessage.append("- ").append(violation.getMessage()).append("\n");
            }
            logger.error(errorMessage.toString());
            throw new IllegalStateException(errorMessage.toString());
        }
        
        // Additional business logic validation
        validateBusinessRules();
        
        logger.info("Configuration validation successful");
        logConfiguration();
    }
    
    private void validateBusinessRules() {
        if (tradingConfig.getSessionStartTime().isAfter(tradingConfig.getSessionEndTime())) {
            throw new IllegalStateException("Session start time must be before session end time");
        }
        
        if (tradingConfig.getStopLossPercentage() >= 100) {
            throw new IllegalStateException("Stop loss percentage must be less than 100%");
        }
        
        if (tradingConfig.getProfitTargetPercentage() <= 0) {
            throw new IllegalStateException("Profit target percentage must be greater than 0%");
        }
        
        if (tradingConfig.getPortfolioRiskPercentage() >= 100) {
            throw new IllegalStateException("Portfolio risk percentage must be less than 100%");
        }
    }
    
    private void logConfiguration() {
        logger.info("Trading Configuration:");
        logger.info("  Session Time: {} - {}", 
                   tradingConfig.getSessionStartTime(), 
                   tradingConfig.getSessionEndTime());
        logger.info("  Cycle Interval: {} minutes", tradingConfig.getCycleIntervalMinutes());
        logger.info("  Number of Cycles: {}", tradingConfig.getNumberOfCycles());
        logger.info("  Position Quantity: {}", tradingConfig.getPositionQuantity());
        logger.info("  Strike Distance: {}", tradingConfig.getStrikeDistance());
        logger.info("  Stop Loss: {}%", tradingConfig.getStopLossPercentage());
        logger.info("  Profit Target: {}%", tradingConfig.getProfitTargetPercentage());
        logger.info("  Portfolio Risk: {}%", tradingConfig.getPortfolioRiskPercentage());
        logger.info("  Binance API URL: {}", tradingConfig.getBinanceApiUrl());
        logger.info("  Order Timeout: {} seconds", tradingConfig.getOrderTimeoutSeconds());
        
        // Don't log sensitive information
        logger.info("  API Key: {}***", 
                   tradingConfig.getApiKey() != null ? tradingConfig.getApiKey().substring(0, 8) : "null");
        logger.info("  Telegram Bot Configured: {}", 
                   tradingConfig.getTelegramBotToken() != null && !tradingConfig.getTelegramBotToken().isEmpty());
    }
}