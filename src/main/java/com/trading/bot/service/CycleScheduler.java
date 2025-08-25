package com.trading.bot.service;

import com.trading.bot.config.TradingConfig;
import com.trading.bot.model.IronButterflyPosition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class CycleScheduler {
    
    private static final Logger logger = LoggerFactory.getLogger(CycleScheduler.class);
    
    @Autowired
    private TradingConfig config;
    
    @Autowired
    private IronButterflyExecutor ironButterflyExecutor;
    
    @Autowired
    private RiskManager riskManager;
    
    @Autowired
    private NotificationService notificationService;
    
    private ScheduledExecutorService cycleExecutor;
    private final AtomicInteger cyclesCompleted = new AtomicInteger(0);
    private volatile boolean schedulingActive = false;
    private LocalDateTime sessionStartTime;
    
    public void startCycleScheduling() {
        if (schedulingActive) {
            return; // Already active
        }
        
        schedulingActive = true;
        sessionStartTime = LocalDateTime.now();
        cyclesCompleted.set(0);
        
        cycleExecutor = Executors.newScheduledThreadPool(1);
        
        logger.info("Starting cycle scheduling - {} cycles every {} minutes", 
                   config.getNumberOfCycles(), config.getCycleIntervalMinutes());
        
        // Execute first cycle immediately
        executeTradingCycle();
        
        // Schedule subsequent cycles
        if (config.getNumberOfCycles() > 1) {
            cycleExecutor.scheduleAtFixedRate(
                this::executeTradingCycle, 
                config.getCycleIntervalMinutes(), 
                config.getCycleIntervalMinutes(), 
                TimeUnit.MINUTES
            );
        }
    }
    
    public void stopCycleScheduling() {
        if (!schedulingActive) {
            return; // Already stopped
        }
        
        schedulingActive = false;
        
        logger.info("Stopping cycle scheduling");
        
        if (cycleExecutor != null && !cycleExecutor.isShutdown()) {
            cycleExecutor.shutdown();
            try {
                if (!cycleExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                    cycleExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                cycleExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
    
    private void executeTradingCycle() {
        if (!schedulingActive) {
            return; // Scheduling stopped
        }
        
        if (riskManager.isPortfolioStopLossTriggered()) {
            logger.warn("Portfolio stop-loss triggered - stopping cycle execution");
            stopCycleScheduling();
            return;
        }
        
        int currentCycle = cyclesCompleted.incrementAndGet();
        
        logger.info("🔄 EXECUTING TRADING CYCLE {}/{}", currentCycle, config.getNumberOfCycles());
        
        try {
            // Send cycle start notification
            String cycleMessage = String.format(
                "🔄 CYCLE %d/%d STARTING\n" +
                "Time: %s\n" +
                "Executing iron butterfly...",
                currentCycle,
                config.getNumberOfCycles(),
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
            );
            notificationService.sendNotification(cycleMessage);
            
            // Execute iron butterfly strategy
            IronButterflyPosition position = ironButterflyExecutor.executeIronButterfly();
            
            logger.info("Cycle {} completed successfully - Position: {}", 
                       currentCycle, position.getPositionId());
            
        } catch (Exception e) {
            logger.error("Cycle {} failed: {}", currentCycle, e.getMessage(), e);
            
            String errorMessage = String.format(
                "❌ CYCLE %d/%d FAILED\n" +
                "Time: %s\n" +
                "Error: %s",
                currentCycle,
                config.getNumberOfCycles(),
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                e.getMessage()
            );
            notificationService.sendAlert(errorMessage);
        }
        
        // Check if we've completed all cycles
        if (currentCycle >= config.getNumberOfCycles()) {
            logger.info("All {} cycles completed - stopping cycle scheduling", config.getNumberOfCycles());
            
            String completionMessage = String.format(
                "✅ ALL CYCLES COMPLETED\n" +
                "Total Cycles: %d\n" +
                "Session Duration: %d minutes\n" +
                "Monitoring positions until session end...",
                config.getNumberOfCycles(),
                getSessionDurationMinutes()
            );
            notificationService.sendNotification(completionMessage);
            
            stopCycleScheduling();
        }
    }
    
    public int getCyclesCompleted() {
        return cyclesCompleted.get();
    }
    
    public boolean isSchedulingActive() {
        return schedulingActive;
    }
    
    public int getRemainingCycles() {
        return Math.max(0, config.getNumberOfCycles() - cyclesCompleted.get());
    }
    
    public long getSessionDurationMinutes() {
        if (sessionStartTime == null) {
            return 0;
        }
        return java.time.Duration.between(sessionStartTime, LocalDateTime.now()).toMinutes();
    }
    
    public LocalDateTime getNextCycleTime() {
        if (!schedulingActive || cyclesCompleted.get() >= config.getNumberOfCycles()) {
            return null;
        }
        
        return LocalDateTime.now().plusMinutes(config.getCycleIntervalMinutes());
    }
    
    public String getCycleStatus() {
        if (!schedulingActive) {
            return "Inactive";
        }
        
        int completed = cyclesCompleted.get();
        int total = config.getNumberOfCycles();
        
        if (completed >= total) {
            return "All cycles completed";
        }
        
        LocalDateTime nextCycle = getNextCycleTime();
        String nextCycleStr = nextCycle != null ? 
                nextCycle.format(DateTimeFormatter.ofPattern("HH:mm:ss")) : "N/A";
        
        return String.format("Cycle %d/%d - Next: %s", completed, total, nextCycleStr);
    }
}