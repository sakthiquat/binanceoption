package com.trading.bot.service;

import com.trading.bot.config.TradingConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class TradingSessionManager {
    
    private static final Logger logger = LoggerFactory.getLogger(TradingSessionManager.class);
    
    @Autowired
    private TradingConfig config;
    
    @Autowired
    private CycleScheduler cycleScheduler;
    
    @Autowired
    private PositionManager positionManager;
    
    @Autowired
    private RiskManager riskManager;
    
    @Autowired
    private NotificationService notificationService;
    
    private ScheduledExecutorService sessionExecutor;
    private volatile boolean sessionActive = false;
    private volatile boolean sessionStarted = false;
    
    public void startTradingSession() {
        logger.info("Initializing trading session...");
        
        sessionExecutor = Executors.newScheduledThreadPool(2);
        
        // Send session start notification
        String startMessage = String.format(
            "🚀 TRADING SESSION STARTING\n" +
            "Session Time: %s - %s\n" +
            "Cycle Interval: %d minutes\n" +
            "Number of Cycles: %d\n" +
            "Position Quantity: %s\n" +
            "Strike Distance: %d",
            config.getSessionStartTime().format(DateTimeFormatter.ofPattern("HH:mm")),
            config.getSessionEndTime().format(DateTimeFormatter.ofPattern("HH:mm")),
            config.getCycleIntervalMinutes(),
            config.getNumberOfCycles(),
            config.getPositionQuantity(),
            config.getStrikeDistance()
        );
        notificationService.sendNotification(startMessage);
        
        // Wait for session start time
        waitForSessionStart();
        
        // Start the actual trading session
        if (!riskManager.isPortfolioStopLossTriggered()) {
            startActiveSession();
        }
    }
    
    private void waitForSessionStart() {
        LocalTime currentTime = LocalTime.now();
        LocalTime startTime = config.getSessionStartTime();
        
        if (currentTime.isBefore(startTime)) {
            long minutesToWait = java.time.Duration.between(currentTime, startTime).toMinutes();
            logger.info("Current time: {} - Waiting {} minutes until session start at {}", 
                       currentTime.format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                       minutesToWait,
                       startTime.format(DateTimeFormatter.ofPattern("HH:mm")));
            
            // Schedule session start
            sessionExecutor.schedule(this::startActiveSession, minutesToWait, TimeUnit.MINUTES);
            
            // Also schedule periodic time checks
            sessionExecutor.scheduleAtFixedRate(this::checkSessionTiming, 0, 30, TimeUnit.SECONDS);
        } else if (currentTime.isBefore(config.getSessionEndTime())) {
            logger.info("Current time {} is within session window - starting immediately", 
                       currentTime.format(DateTimeFormatter.ofPattern("HH:mm:ss")));
            startActiveSession();
        } else {
            logger.warn("Current time {} is after session end time {} - session will not start today", 
                       currentTime.format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                       config.getSessionEndTime().format(DateTimeFormatter.ofPattern("HH:mm")));
            
            String lateMessage = String.format(
                "⏰ SESSION MISSED\n" +
                "Current time: %s\n" +
                "Session end time: %s\n" +
                "Session will not start today",
                currentTime.format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                config.getSessionEndTime().format(DateTimeFormatter.ofPattern("HH:mm"))
            );
            notificationService.sendAlert(lateMessage);
        }
    }
    
    private void checkSessionTiming() {
        LocalTime currentTime = LocalTime.now();
        
        if (!sessionStarted && currentTime.equals(config.getSessionStartTime()) || 
            currentTime.isAfter(config.getSessionStartTime()) && currentTime.isBefore(config.getSessionEndTime())) {
            if (!sessionActive) {
                startActiveSession();
            }
        } else if (currentTime.isAfter(config.getSessionEndTime()) || currentTime.equals(config.getSessionEndTime())) {
            if (sessionActive) {
                endSession("Session end time reached");
            }
        }
    }
    
    private void startActiveSession() {
        if (sessionStarted) {
            return; // Already started
        }
        
        sessionStarted = true;
        sessionActive = true;
        
        logger.info("🚀 TRADING SESSION STARTED at {}", 
                   LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        
        // Send session active notification
        String activeMessage = String.format(
            "✅ TRADING SESSION ACTIVE\n" +
            "Start Time: %s\n" +
            "End Time: %s\n" +
            "Starting trading cycles...",
            LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")),
            config.getSessionEndTime().format(DateTimeFormatter.ofPattern("HH:mm"))
        );
        notificationService.sendNotification(activeMessage);
        
        // Start cycle scheduling
        cycleScheduler.startCycleScheduling();
        
        // Schedule session end
        LocalTime endTime = config.getSessionEndTime();
        LocalTime currentTime = LocalTime.now();
        
        if (currentTime.isBefore(endTime)) {
            long minutesToEnd = java.time.Duration.between(currentTime, endTime).toMinutes();
            sessionExecutor.schedule(() -> endSession("Session end time reached"), minutesToEnd, TimeUnit.MINUTES);
        }
    }
    
    public void endSession(String reason) {
        if (!sessionActive) {
            return; // Already ended
        }
        
        sessionActive = false;
        
        logger.info("🛑 ENDING TRADING SESSION - Reason: {}", reason);
        
        // Stop cycle scheduling
        cycleScheduler.stopCycleScheduling();
        
        // Close all open positions
        int openPositions = positionManager.getOpenPositionCount();
        if (openPositions > 0) {
            logger.info("Closing {} open positions due to session end", openPositions);
            positionManager.closeAllPositions(reason);
        }
        
        // Calculate session summary
        SessionSummary summary = calculateSessionSummary();
        
        // Send session end notification
        String endMessage = String.format(
            "🏁 TRADING SESSION ENDED\n" +
            "Reason: %s\n" +
            "End Time: %s\n" +
            "Cycles Completed: %d/%d\n" +
            "Total Positions: %d\n" +
            "Session Duration: %s minutes",
            reason,
            LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")),
            summary.getCyclesCompleted(),
            config.getNumberOfCycles(),
            summary.getTotalPositions(),
            summary.getSessionDurationMinutes()
        );
        notificationService.sendNotification(endMessage);
        
        // Shutdown executors
        if (sessionExecutor != null && !sessionExecutor.isShutdown()) {
            sessionExecutor.shutdown();
            try {
                if (!sessionExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    sessionExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                sessionExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        logger.info("Trading session ended successfully");
    }
    
    private SessionSummary calculateSessionSummary() {
        int cyclesCompleted = cycleScheduler.getCyclesCompleted();
        int totalPositions = positionManager.getAllPositions().size();
        
        // Calculate session duration (simplified)
        long durationMinutes = java.time.Duration.between(
                config.getSessionStartTime().atDate(java.time.LocalDate.now()),
                LocalDateTime.now()
        ).toMinutes();
        
        return new SessionSummary(cyclesCompleted, totalPositions, durationMinutes);
    }
    
    public boolean isSessionActive() {
        return sessionActive;
    }
    
    public boolean isSessionStarted() {
        return sessionStarted;
    }
    
    public boolean isWithinSessionWindow() {
        LocalTime currentTime = LocalTime.now();
        return currentTime.isAfter(config.getSessionStartTime()) && 
               currentTime.isBefore(config.getSessionEndTime());
    }
    
    // Inner class for session summary
    private static class SessionSummary {
        private final int cyclesCompleted;
        private final int totalPositions;
        private final long sessionDurationMinutes;
        
        public SessionSummary(int cyclesCompleted, int totalPositions, long sessionDurationMinutes) {
            this.cyclesCompleted = cyclesCompleted;
            this.totalPositions = totalPositions;
            this.sessionDurationMinutes = sessionDurationMinutes;
        }
        
        public int getCyclesCompleted() { return cyclesCompleted; }
        public int getTotalPositions() { return totalPositions; }
        public long getSessionDurationMinutes() { return sessionDurationMinutes; }
    }
}