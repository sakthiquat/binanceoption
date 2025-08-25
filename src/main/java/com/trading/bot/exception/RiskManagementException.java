package com.trading.bot.exception;

/**
 * Exception thrown when risk management violations occur.
 * These are critical errors that require immediate position closure.
 */
public class RiskManagementException extends TradingBotException {
    
    private final String riskType;
    private final double currentValue;
    private final double threshold;
    
    public RiskManagementException(String message) {
        super(message, "RISK_VIOLATION", false);
        this.riskType = "UNKNOWN";
        this.currentValue = 0.0;
        this.threshold = 0.0;
    }
    
    public RiskManagementException(String message, String riskType, double currentValue, double threshold) {
        super(message, "RISK_VIOLATION", false);
        this.riskType = riskType;
        this.currentValue = currentValue;
        this.threshold = threshold;
    }
    
    public RiskManagementException(String message, Throwable cause, String riskType, double currentValue, double threshold) {
        super(message, cause, "RISK_VIOLATION", false);
        this.riskType = riskType;
        this.currentValue = currentValue;
        this.threshold = threshold;
    }
    
    public String getRiskType() {
        return riskType;
    }
    
    public double getCurrentValue() {
        return currentValue;
    }
    
    public double getThreshold() {
        return threshold;
    }
    
    @Override
    public String getFormattedMessage() {
        return String.format("%s Risk Type: %s, Current: %.2f, Threshold: %.2f", 
                           super.getFormattedMessage(), riskType, currentValue, threshold);
    }
    
    /**
     * Creates a portfolio stop-loss exception
     */
    public static RiskManagementException portfolioStopLoss(double currentMTM, double threshold) {
        return new RiskManagementException(
            String.format("Portfolio stop-loss triggered: MTM %.2f exceeds threshold %.2f", currentMTM, threshold),
            "PORTFOLIO_STOP_LOSS",
            currentMTM,
            threshold
        );
    }
    
    /**
     * Creates a position stop-loss exception
     */
    public static RiskManagementException positionStopLoss(String positionId, double currentPnL, double threshold) {
        return new RiskManagementException(
            String.format("Position %s stop-loss triggered: P&L %.2f%% exceeds threshold %.2f%%", 
                         positionId, currentPnL, threshold),
            "POSITION_STOP_LOSS",
            currentPnL,
            threshold
        );
    }
}