package com.trading.bot.controller;

import com.trading.bot.service.TradingSessionManager;
import com.trading.bot.service.RiskManager;
import com.trading.bot.service.PositionManager;
import com.trading.bot.service.CycleScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;

@Component
public class TradingBotController implements CommandLineRunner {
    
    private static final Logger logger = LoggerFactory.getLogger(TradingBotController.class);
    
    @Autowired
    private TradingSessionManager sessionManager;
    
    @Autowired
    private RiskManager riskManager;
    
    @Autowired
    private PositionManager positionManager;
    
    @Autowired
    private CycleScheduler cycleScheduler;
    
    @Override
    public void run(String... args) throws Exception {
        logger.info("🤖 BTC Options Straddle Bot Starting...");
        
        try {
            // Start the trading session
            sessionManager.startTradingSession();
            
            // Keep the application running
            while (sessionManager.isSessionActive() && !riskManager.isPortfolioStopLossTriggered()) {
                Thread.sleep(5000); // Check every 5 seconds
            }
            
            logger.info("Trading session completed or stopped");
            
        } catch (Exception e) {
            logger.error("Fatal error in trading bot: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    @PreDestroy
    public void shutdown() {
        logger.info("Shutting down BTC Options Straddle Bot...");
        
        try {
            // End session if still active
            if (sessionManager.isSessionActive()) {
                sessionManager.endSession("Application shutdown");
            }
            
            // Stop all services
            cycleScheduler.stopCycleScheduling();
            positionManager.stopPositionMonitoring();
            riskManager.stopRiskMonitoring();
            
            logger.info("BTC Options Straddle Bot shutdown completed");
            
        } catch (Exception e) {
            logger.error("Error during shutdown: {}", e.getMessage(), e);
        }
    }
}