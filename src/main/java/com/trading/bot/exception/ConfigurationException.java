package com.trading.bot.exception;

/**
 * Exception thrown when configuration loading or validation fails.
 * These errors are typically non-recoverable and require application restart.
 */
public class ConfigurationException extends TradingBotException {
    
    private final String configurationKey;
    
    public ConfigurationException(String message) {
        super(message, "CONFIG_ERROR", false);
        this.configurationKey = null;
    }
    
    public ConfigurationException(String message, String configurationKey) {
        super(message, "CONFIG_ERROR", false);
        this.configurationKey = configurationKey;
    }
    
    public ConfigurationException(String message, Throwable cause) {
        super(message, cause, "CONFIG_ERROR", false);
        this.configurationKey = null;
    }
    
    public ConfigurationException(String message, Throwable cause, String configurationKey) {
        super(message, cause, "CONFIG_ERROR", false);
        this.configurationKey = configurationKey;
    }
    
    public String getConfigurationKey() {
        return configurationKey;
    }
    
    @Override
    public String getFormattedMessage() {
        if (configurationKey != null) {
            return String.format("%s Configuration Key: %s", super.getFormattedMessage(), configurationKey);
        }
        return super.getFormattedMessage();
    }
}