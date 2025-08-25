package com.trading.bot.exception;

/**
 * Exception thrown when Binance API operations fail.
 * Includes HTTP status codes and API-specific error information.
 */
public class APIException extends TradingBotException {
    
    private final int httpStatusCode;
    private final String apiErrorCode;
    
    public APIException(String message) {
        super(message, "API_ERROR", true);
        this.httpStatusCode = -1;
        this.apiErrorCode = null;
    }
    
    public APIException(String message, Throwable cause) {
        super(message, cause, "API_ERROR", true);
        this.httpStatusCode = -1;
        this.apiErrorCode = null;
    }
    
    public APIException(String message, int httpStatusCode) {
        super(message, "API_ERROR", isRecoverableStatus(httpStatusCode, null));
        this.httpStatusCode = httpStatusCode;
        this.apiErrorCode = null;
    }
    
    public APIException(String message, int httpStatusCode, String apiErrorCode) {
        super(message, "API_ERROR", isRecoverableStatus(httpStatusCode, apiErrorCode));
        this.httpStatusCode = httpStatusCode;
        this.apiErrorCode = apiErrorCode;
    }
    
    public APIException(String message, Throwable cause, int httpStatusCode, String apiErrorCode) {
        super(message, cause, "API_ERROR", isRecoverableStatus(httpStatusCode, apiErrorCode));
        this.httpStatusCode = httpStatusCode;
        this.apiErrorCode = apiErrorCode;
    }
    
    private static boolean isRecoverableStatus(int httpStatusCode, String apiErrorCode) {
        // Rate limit errors are recoverable
        if (httpStatusCode == 429 || (apiErrorCode != null && apiErrorCode.contains("RATE_LIMIT"))) {
            return true;
        }
        // 4xx and 5xx errors are generally not recoverable
        if (httpStatusCode >= 400) {
            return false;
        }
        // Other errors default to recoverable
        return true;
    }
    
    public int getHttpStatusCode() {
        return httpStatusCode;
    }
    
    public String getApiErrorCode() {
        return apiErrorCode;
    }
    
    @Override
    public String getFormattedMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.getFormattedMessage());
        
        if (httpStatusCode > 0) {
            sb.append(" HTTP Status: ").append(httpStatusCode);
        }
        
        if (apiErrorCode != null) {
            sb.append(" API Error: ").append(apiErrorCode);
        }
        
        return sb.toString();
    }
    
    /**
     * Determines if the API error is due to rate limiting
     */
    public boolean isRateLimitError() {
        return httpStatusCode == 429 || 
               (apiErrorCode != null && apiErrorCode.contains("RATE_LIMIT"));
    }
    
    /**
     * Determines if the API error is due to authentication issues
     */
    public boolean isAuthenticationError() {
        return httpStatusCode == 401 || httpStatusCode == 403 ||
               (apiErrorCode != null && (apiErrorCode.contains("INVALID_SIGNATURE") || 
                                       apiErrorCode.contains("INVALID_API_KEY")));
    }
}