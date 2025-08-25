package com.trading.bot.service;

import com.trading.bot.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class LoggingService {
    
    private static final Logger tradingLogger = LoggerFactory.getLogger("TRADING");
    private static final Logger orderLogger = LoggerFactory.getLogger("ORDERS");
    private static final Logger positionLogger = LoggerFactory.getLogger("POSITIONS");
    private static final Logger riskLogger = LoggerFactory.getLogger("RISK");
    private static final Logger sessionLogger = LoggerFactory.getLogger("SESSION");
    
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    
    public void logTradingAction(String action, String details) {
        setTimestamp();
        tradingLogger.info("ACTION: {} | DETAILS: {}", action, details);
        clearMDC();
    }
    
    public void logOrderPlacement(OrderRequest orderRequest, String orderId) {
        setTimestamp();
        MDC.put("orderId", orderId);
        MDC.put("symbol", orderRequest.getSymbol());
        
        orderLogger.info("ORDER_PLACED | ID: {} | SYMBOL: {} | SIDE: {} | QTY: {} | PRICE: {} | TYPE: {}", 
                        orderId,
                        orderRequest.getSymbol(),
                        orderRequest.getSide(),
                        orderRequest.getQuantity(),
                        orderRequest.getPrice(),
                        orderRequest.getType());
        clearMDC();
    }
    
    public void logOrderFill(OrderResponse orderResponse) {
        setTimestamp();
        MDC.put("orderId", orderResponse.getOrderId());
        MDC.put("symbol", orderResponse.getSymbol());
        
        orderLogger.info("ORDER_FILLED | ID: {} | SYMBOL: {} | SIDE: {} | FILLED_QTY: {} | AVG_PRICE: {} | STATUS: {}", 
                        orderResponse.getOrderId(),
                        orderResponse.getSymbol(),
                        orderResponse.getSide(),
                        orderResponse.getFilledQuantity(),
                        orderResponse.getAvgPrice(),
                        orderResponse.getStatus());
        clearMDC();
    }
    
    public void logOrderModification(String orderId, String symbol, BigDecimal oldPrice, BigDecimal newPrice) {
        setTimestamp();
        MDC.put("orderId", orderId);
        MDC.put("symbol", symbol);
        
        orderLogger.info("ORDER_MODIFIED | ID: {} | SYMBOL: {} | OLD_PRICE: {} | NEW_PRICE: {}", 
                        orderId, symbol, oldPrice, newPrice);
        clearMDC();
    }
    
    public void logOrderTimeout(String orderId, String symbol, int timeoutSeconds) {
        setTimestamp();
        MDC.put("orderId", orderId);
        MDC.put("symbol", symbol);
        
        orderLogger.warn("ORDER_TIMEOUT | ID: {} | SYMBOL: {} | TIMEOUT: {}s", 
                        orderId, symbol, timeoutSeconds);
        clearMDC();
    }
    
    public void logPositionCreation(IronButterflyPosition position) {
        setTimestamp();
        MDC.put("positionId", position.getPositionId());
        MDC.put("atmStrike", position.getAtmStrike().toString());
        
        positionLogger.info("POSITION_CREATED | ID: {} | ATM_STRIKE: {} | MAX_LOSS: {} | STATUS: {}", 
                           position.getPositionId(),
                           position.getAtmStrike(),
                           position.getMaxTheoreticalLoss(),
                           position.getStatus());
        
        // Log individual legs
        logOptionLeg("SELL_CALL", position.getSellCall());
        logOptionLeg("SELL_PUT", position.getSellPut());
        logOptionLeg("BUY_CALL", position.getBuyCall());
        logOptionLeg("BUY_PUT", position.getBuyPut());
        
        clearMDC();
    }
    
    private void logOptionLeg(String legType, OptionLeg leg) {
        if (leg != null) {
            positionLogger.info("POSITION_LEG | TYPE: {} | SYMBOL: {} | STRIKE: {} | SIDE: {} | QTY: {} | ENTRY_PRICE: {}", 
                               legType,
                               leg.getSymbol(),
                               leg.getStrike(),
                               leg.getSide(),
                               leg.getQuantity(),
                               leg.getEntryPrice());
        }
    }
    
    public void logPositionClosure(IronButterflyPosition position, String reason) {
        setTimestamp();
        MDC.put("positionId", position.getPositionId());
        
        BigDecimal finalPnL = position.calculateCurrentPnL();
        
        positionLogger.info("POSITION_CLOSED | ID: {} | REASON: {} | FINAL_PNL: {} | STATUS: {} | DURATION: {}min", 
                           position.getPositionId(),
                           reason,
                           finalPnL,
                           position.getStatus(),
                           calculatePositionDuration(position));
        clearMDC();
    }
    
    private long calculatePositionDuration(IronButterflyPosition position) {
        if (position.getCreationTime() != null) {
            return java.time.Duration.between(position.getCreationTime(), LocalDateTime.now()).toMinutes();
        }
        return 0;
    }
    
    public void logRiskEvent(String eventType, String details, BigDecimal value) {
        setTimestamp();
        MDC.put("eventType", eventType);
        
        riskLogger.warn("RISK_EVENT | TYPE: {} | VALUE: {} | DETAILS: {}", eventType, value, details);
        clearMDC();
    }
    
    public void logPortfolioRisk(int openPositions, BigDecimal totalMaxLoss, BigDecimal currentMTM, double riskPercentage) {
        setTimestamp();
        
        riskLogger.info("PORTFOLIO_RISK | POSITIONS: {} | MAX_LOSS: {} | CURRENT_MTM: {} | RISK_UTIL: {}%", 
                       openPositions, totalMaxLoss, currentMTM, String.format("%.2f", riskPercentage));
        clearMDC();
    }
    
    public void logSessionStart(LocalDateTime startTime, String sessionConfig) {
        setTimestamp();
        
        sessionLogger.info("SESSION_STARTED | START_TIME: {} | CONFIG: {}", 
                          startTime.format(TIMESTAMP_FORMAT), sessionConfig);
        clearMDC();
    }
    
    public void logSessionEnd(LocalDateTime endTime, String reason, int cyclesCompleted, int totalPositions) {
        setTimestamp();
        
        sessionLogger.info("SESSION_ENDED | END_TIME: {} | REASON: {} | CYCLES: {} | POSITIONS: {}", 
                          endTime.format(TIMESTAMP_FORMAT), reason, cyclesCompleted, totalPositions);
        clearMDC();
    }
    
    public void logCycleExecution(int cycleNumber, int totalCycles, boolean success, String details) {
        setTimestamp();
        MDC.put("cycleNumber", String.valueOf(cycleNumber));
        
        if (success) {
            sessionLogger.info("CYCLE_COMPLETED | CYCLE: {}/{} | DETAILS: {}", cycleNumber, totalCycles, details);
        } else {
            sessionLogger.error("CYCLE_FAILED | CYCLE: {}/{} | ERROR: {}", cycleNumber, totalCycles, details);
        }
        clearMDC();
    }
    
    public void logMarketData(BigDecimal btcPrice, int optionContractsCount, String expiry) {
        setTimestamp();
        
        tradingLogger.info("MARKET_DATA | BTC_PRICE: {} | OPTION_CONTRACTS: {} | EXPIRY: {}", 
                          btcPrice, optionContractsCount, expiry);
        clearMDC();
    }
    
    public void logError(String component, String operation, String error, Exception exception) {
        setTimestamp();
        MDC.put("component", component);
        MDC.put("operation", operation);
        
        Logger logger = LoggerFactory.getLogger(component);
        if (exception != null) {
            logger.error("ERROR | OPERATION: {} | MESSAGE: {}", operation, error, exception);
        } else {
            logger.error("ERROR | OPERATION: {} | MESSAGE: {}", operation, error);
        }
        clearMDC();
    }
    
    public void logSessionSummary(SessionSummaryData summary) {
        setTimestamp();
        
        sessionLogger.info("SESSION_SUMMARY | DURATION: {}min | CYCLES: {}/{} | POSITIONS: {} | TOTAL_PNL: {} | SUCCESS_RATE: {}%", 
                          summary.getDurationMinutes(),
                          summary.getCyclesCompleted(),
                          summary.getTotalCycles(),
                          summary.getTotalPositions(),
                          summary.getTotalPnL(),
                          summary.getSuccessRate());
        
        // Log detailed position breakdown
        if (summary.getPositionBreakdown() != null) {
            for (String breakdown : summary.getPositionBreakdown()) {
                sessionLogger.info("POSITION_BREAKDOWN | {}", breakdown);
            }
        }
        
        clearMDC();
    }
    
    private void setTimestamp() {
        MDC.put("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMAT));
    }
    
    private void clearMDC() {
        MDC.clear();
    }
    
    // Data class for session summary
    public static class SessionSummaryData {
        private final long durationMinutes;
        private final int cyclesCompleted;
        private final int totalCycles;
        private final int totalPositions;
        private final BigDecimal totalPnL;
        private final double successRate;
        private final List<String> positionBreakdown;
        
        public SessionSummaryData(long durationMinutes, int cyclesCompleted, int totalCycles, 
                                 int totalPositions, BigDecimal totalPnL, double successRate, 
                                 List<String> positionBreakdown) {
            this.durationMinutes = durationMinutes;
            this.cyclesCompleted = cyclesCompleted;
            this.totalCycles = totalCycles;
            this.totalPositions = totalPositions;
            this.totalPnL = totalPnL;
            this.successRate = successRate;
            this.positionBreakdown = positionBreakdown;
        }
        
        // Getters
        public long getDurationMinutes() { return durationMinutes; }
        public int getCyclesCompleted() { return cyclesCompleted; }
        public int getTotalCycles() { return totalCycles; }
        public int getTotalPositions() { return totalPositions; }
        public BigDecimal getTotalPnL() { return totalPnL; }
        public double getSuccessRate() { return successRate; }
        public List<String> getPositionBreakdown() { return positionBreakdown; }
    }
}