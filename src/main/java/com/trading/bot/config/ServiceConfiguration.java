package com.trading.bot.config;

import com.trading.bot.service.PositionManager;
import com.trading.bot.service.RiskManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

@Configuration
public class ServiceConfiguration {
    
    @Autowired
    private PositionManager positionManager;
    
    @Autowired
    private RiskManager riskManager;
    
    @PostConstruct
    public void configureServices() {
        // Resolve circular dependency by setting RiskManager in PositionManager
        positionManager.setRiskManager(riskManager);
    }
}