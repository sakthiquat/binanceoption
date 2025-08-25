package com.trading.bot.service;

import com.trading.bot.config.TradingConfig;
import com.trading.bot.model.IronButterflyPosition;
import com.trading.bot.model.PositionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class RiskManager {
    
    private static final Logger logger = LoggerFactory.getLogger(RiskManager.class);
    
    @Autowired
    private TradingConfig config;
    
    @Autowired
    private PositionManager positionManager;
    
    @Autowired
    private NotificationService notificationService;
    
    private ScheduledExecutorService riskMonitoringExecutor;
    private volatile boolean portfolioStopLossTriggered = false;
    
    @PostConstruct
    public void init() {
        this.riskMonitoringExecutor = Executors.newScheduledThreadPool(1);
        startRiskMonitoring();
        logger.info("Risk Manager initialized");
    }
    
    @PreDestroy
    public void cleanup() {
        stopRiskMonitoring();
    }
    
    public void startRiskMonitoring() {
        logger.info("Starting portfolio risk monitoring (1-second interval)");
        
        riskMonitoringExecutor.scheduleAtFixedRate(this::monitorPortfolioRisk, 0, 1, TimeUnit.SECONDS);
    }
    
    public void stopRiskMonitoring() {
        logger.info("Stopping portfolio risk monitoring");
        
        if (riskMonitoringExecutor != null && !riskMonitoringExecutor.isShutdown()) {
            riskMonitoringExecutor.shutdown();
            try {
                if (!riskMonitoringExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    riskMonitoringExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                riskMonitoringExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
    
    private void monitorPortfolioRisk() {
        if (portfolioStopLossTriggered) {
            return; // Already triggered, no need to continue monitoring
        }
        
        try {
            List<IronButterflyPosition> openPositions = positionManager.getOpenPositions();
            
            if (openPositions.isEmpty()) {
                return;
            }
            
            // Calculate portfolio-level risk metrics
            PortfolioRiskMetrics metrics = calculatePortfolioRisk(openPositions);
            
            // Check if portfolio stop-loss should be triggered
            if (shouldTriggerPortfolioStopLoss(metrics)) {
                triggerPortfolioStopLoss(metrics);
            }
            
            // Log risk metrics periodically (every 30 seconds)
            if (System.currentTimeMillis() % 30000 < 1000) {
                logRiskMetrics(metrics);
            }
            
        } catch (Exception e) {
            logger.warn("Error during portfolio risk monitoring: {}", e.getMessage());
        }
    }
    
    public PortfolioRiskMetrics calculatePortfolioRisk(List<IronButterflyPosition> positions) {
        BigDecimal totalMaxTheoreticalLoss = BigDecimal.ZERO;
        BigDecimal totalCurrentMTM = BigDecimal.ZERO;
        int totalPositions = positions.size();
        int positionsWithPrices = 0;
        
        for (IronButterflyPosition position : positions) {
            // Add maximum theoretical loss
            if (position.getMaxTheoreticalLoss() != null) {
                totalMaxTheoreticalLoss = totalMaxTheoreticalLoss.add(position.getMaxTheoreticalLoss());
            }
            
            // Add current mark-to-market
            BigDecimal currentPnL = position.calculateCurrentPnL();
            if (currentPnL != null) {
                totalCurrentMTM = totalCurrentMTM.add(currentPnL);
                positionsWithPrices++;
            }
        }
        
        return new PortfolioRiskMetrics(
                totalMaxTheoreticalLoss,
                totalCurrentMTM,
                totalPositions,
                positionsWithPrices
        );
    }
    
    private boolean shouldTriggerPortfolioStopLoss(PortfolioRiskMetrics metrics) {
        if (metrics.getTotalMaxTheoreticalLoss().compareTo(BigDecimal.ZERO) <= 0) {
            return false; // No risk to manage
        }
        
        BigDecimal riskThreshold = metrics.getTotalMaxTheoreticalLoss()
                .multiply(BigDecimal.valueOf(config.getPortfolioRiskPercentage() / 100.0));
        
        // Trigger if current MTM loss exceeds the risk threshold
        return metrics.getTotalCurrentMTM().compareTo(riskThreshold.negate()) <= 0;
    }
    
    private void triggerPortfolioStopLoss(PortfolioRiskMetrics metrics) {
        portfolioStopLossTriggered = true;
        
        logger.error("PORTFOLIO STOP-LOSS TRIGGERED!");
        logger.error("  Total Max Loss: {}", metrics.getTotalMaxTheoreticalLoss());
        logger.error("  Current MTM: {}", metrics.getTotalCurrentMTM());
        logger.error("  Risk Threshold: {}%", config.getPortfolioRiskPercentage());
        
        // Send critical alert
        String alertMessage = String.format(
            "🚨 PORTFOLIO STOP-LOSS TRIGGERED 🚨\n" +
            "Total Positions: %d\n" +
            "Max Theoretical Loss: %s\n" +
            "Current MTM: %s\n" +
            "Risk Threshold: %.1f%%\n" +
            "CLOSING ALL POSITIONS IMMEDIATELY",
            metrics.getTotalPositions(),
            metrics.getTotalMaxTheoreticalLoss(),
            metrics.getTotalCurrentMTM(),
            config.getPortfolioRiskPercentage()
        );
        
        notificationService.sendAlert(alertMessage);
        
        // Close all positions immediately
        positionManager.closeAllPositions("Portfolio stop-loss triggered");
        
        // Stop the application
        logger.error("Shutting down application due to portfolio stop-loss");
        System.exit(1);
    }
    
    private void logRiskMetrics(PortfolioRiskMetrics metrics) {
        if (metrics.getTotalPositions() > 0) {
            BigDecimal riskPercentage = BigDecimal.ZERO;
            
            if (metrics.getTotalMaxTheoreticalLoss().compareTo(BigDecimal.ZERO) > 0) {
                riskPercentage = metrics.getTotalCurrentMTM()
                        .divide(metrics.getTotalMaxTheoreticalLoss(), 4, BigDecimal.ROUND_HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
            }
            
            logger.info("Portfolio Risk Metrics:");
            logger.info("  Open Positions: {}", metrics.getTotalPositions());
            logger.info("  Positions with Prices: {}", metrics.getPositionsWithPrices());
            logger.info("  Total Max Loss: {}", metrics.getTotalMaxTheoreticalLoss());
            logger.info("  Current MTM: {}", metrics.getTotalCurrentMTM());
            logger.info("  Risk Utilization: {}% (Limit: {}%)", 
                       riskPercentage, config.getPortfolioRiskPercentage());
        }
    }
    
    public boolean checkIndividualPositionRisk(IronButterflyPosition position) {
        BigDecimal currentPnL = position.calculateCurrentPnL();
        
        if (currentPnL == null) {
            return false; // Cannot assess risk without prices
        }
        
        BigDecimal netPremium = calculateNetPremiumReceived(position);
        
        if (netPremium.compareTo(BigDecimal.ZERO) <= 0) {
            return false; // No premium received, cannot calculate percentage risk
        }
        
        // Calculate stop-loss and profit target thresholds
        BigDecimal stopLossThreshold = netPremium.multiply(
                BigDecimal.valueOf(-config.getStopLossPercentage() / 100.0));
        BigDecimal profitTargetThreshold = netPremium.multiply(
                BigDecimal.valueOf(config.getProfitTargetPercentage() / 100.0));
        
        if (currentPnL.compareTo(stopLossThreshold) <= 0) {
            logger.warn("Position {} stop-loss triggered: P&L {} <= {}", 
                       position.getPositionId(), currentPnL, stopLossThreshold);
            
            positionManager.closePosition(position, PositionStatus.CLOSED_LOSS, 
                    String.format("Stop-loss: %.1f%%", config.getStopLossPercentage()));
            return true;
        }
        
        if (currentPnL.compareTo(profitTargetThreshold) >= 0) {
            logger.info("Position {} profit target reached: P&L {} >= {}", 
                       position.getPositionId(), currentPnL, profitTargetThreshold);
            
            positionManager.closePosition(position, PositionStatus.CLOSED_PROFIT, 
                    String.format("Profit target: %.1f%%", config.getProfitTargetPercentage()));
            return true;
        }
        
        return false;
    }
    
    private BigDecimal calculateNetPremiumReceived(IronButterflyPosition position) {
        BigDecimal premiumReceived = BigDecimal.ZERO;
        BigDecimal premiumPaid = BigDecimal.ZERO;
        
        if (position.getSellCall() != null && position.getSellCall().getEntryPrice() != null) {
            premiumReceived = premiumReceived.add(
                    position.getSellCall().getEntryPrice().multiply(position.getSellCall().getQuantity()));
        }
        
        if (position.getSellPut() != null && position.getSellPut().getEntryPrice() != null) {
            premiumReceived = premiumReceived.add(
                    position.getSellPut().getEntryPrice().multiply(position.getSellPut().getQuantity()));
        }
        
        if (position.getBuyCall() != null && position.getBuyCall().getEntryPrice() != null) {
            premiumPaid = premiumPaid.add(
                    position.getBuyCall().getEntryPrice().multiply(position.getBuyCall().getQuantity()));
        }
        
        if (position.getBuyPut() != null && position.getBuyPut().getEntryPrice() != null) {
            premiumPaid = premiumPaid.add(
                    position.getBuyPut().getEntryPrice().multiply(position.getBuyPut().getQuantity()));
        }
        
        return premiumReceived.subtract(premiumPaid);
    }
    
    public boolean isPortfolioStopLossTriggered() {
        return portfolioStopLossTriggered;
    }
    
    public void resetPortfolioStopLoss() {
        portfolioStopLossTriggered = false;
        logger.info("Portfolio stop-loss flag reset");
    }
    
    // Inner class for portfolio risk metrics
    public static class PortfolioRiskMetrics {
        private final BigDecimal totalMaxTheoreticalLoss;
        private final BigDecimal totalCurrentMTM;
        private final int totalPositions;
        private final int positionsWithPrices;
        
        public PortfolioRiskMetrics(BigDecimal totalMaxTheoreticalLoss, BigDecimal totalCurrentMTM, 
                                  int totalPositions, int positionsWithPrices) {
            this.totalMaxTheoreticalLoss = totalMaxTheoreticalLoss;
            this.totalCurrentMTM = totalCurrentMTM;
            this.totalPositions = totalPositions;
            this.positionsWithPrices = positionsWithPrices;
        }
        
        public BigDecimal getTotalMaxTheoreticalLoss() { return totalMaxTheoreticalLoss; }
        public BigDecimal getTotalCurrentMTM() { return totalCurrentMTM; }
        public int getTotalPositions() { return totalPositions; }
        public int getPositionsWithPrices() { return positionsWithPrices; }
    }
}