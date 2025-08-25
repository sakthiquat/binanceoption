package com.trading.bot.exception;

/**
 * Base exception class for all trading bot related exceptions.
 * Provides common functionality for error handling and logging.
 */
public class TradingBotException extends Exception {
    
    private final String errorCode;
    private final boolean recoverable;
    
    public TradingBotException(String message) {
        super(message);
        this.errorCode = "GENERAL_ERROR";
        this.recoverable = true;
    }
    
    public TradingBotException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "GENERAL_ERROR";
        this.recoverable = true;
    }
    
    public TradingBotException(String message, String errorCode, boolean recoverable) {
        super(message);
        this.errorCode = errorCode;
        this.recoverable = recoverable;
    }
    
    public TradingBotException(String message, Throwable cause, String errorCode, boolean recoverable) {
        super(message, cause);
        this.errorCode = errorCode;
        this.recoverable = recoverable;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    public boolean isRecoverable() {
        return recoverable;
    }
    
    /**
     * Get a formatted error message including error code and recoverability
     */
    public String getFormattedMessage() {
        return String.format("[%s] %s (Recoverable: %s)", 
                           errorCode, getMessage(), recoverable);
    }
}