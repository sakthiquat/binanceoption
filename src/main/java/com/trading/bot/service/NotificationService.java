package com.trading.bot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.bot.config.TradingConfig;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class NotificationService {
    
    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);
    
    @Autowired
    private TradingConfig config;
    
    private OkHttpClient httpClient;
    private ObjectMapper objectMapper;
    private boolean telegramEnabled = false;
    
    @PostConstruct
    public void init() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
        
        this.objectMapper = new ObjectMapper();
        
        // Check if Telegram is configured
        if (config.getTelegramBotToken() != null && !config.getTelegramBotToken().isEmpty() &&
            config.getTelegramChatId() != null && !config.getTelegramChatId().isEmpty()) {
            telegramEnabled = true;
            logger.info("Telegram notifications enabled");
        } else {
            logger.info("Telegram notifications disabled - missing bot token or chat ID");
        }
    }
    
    public void sendAlert(String message) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String formattedMessage = String.format("🚨 ALERT [%s]\n%s", timestamp, message);
        
        logger.warn("ALERT: {}", message);
        
        if (telegramEnabled) {
            sendTelegramMessageAsync(formattedMessage, true);
        }
    }
    
    public void sendNotification(String message) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String formattedMessage = String.format("ℹ️ INFO [%s]\n%s", timestamp, message);
        
        logger.info("NOTIFICATION: {}", message);
        
        if (telegramEnabled) {
            sendTelegramMessageAsync(formattedMessage, false);
        }
    }
    
    public void sendTradingAction(String action, String details) {
        String message = String.format("📊 TRADING ACTION\n%s\n%s", action, details);
        logger.info("TRADING ACTION: {} - {}", action, details);
        
        if (telegramEnabled) {
            sendTelegramMessageAsync(message, false);
        }
    }
    
    public void sendPositionUpdate(String positionId, String status, String pnl) {
        String message = String.format("💼 POSITION UPDATE\nID: %s\nStatus: %s\nP&L: %s", positionId, status, pnl);
        logger.info("POSITION UPDATE: {} - {} - {}", positionId, status, pnl);
        
        if (telegramEnabled) {
            sendTelegramMessageAsync(message, false);
        }
    }
    
    public void sendSessionSummary(String summary) {
        String message = String.format("📈 SESSION SUMMARY\n%s", summary);
        logger.info("SESSION SUMMARY: {}", summary);
        
        if (telegramEnabled) {
            sendTelegramMessageAsync(message, false);
        }
    }
    
    private void sendTelegramMessageAsync(String message, boolean isAlert) {
        CompletableFuture.runAsync(() -> {
            try {
                sendTelegramMessage(message);
            } catch (Exception e) {
                logger.error("Failed to send Telegram {}: {}", isAlert ? "alert" : "notification", e.getMessage());
            }
        });
    }
    
    private void sendTelegramMessage(String message) throws IOException {
        String url = String.format("https://api.telegram.org/bot%s/sendMessage", config.getTelegramBotToken());
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("chat_id", config.getTelegramChatId());
        payload.put("text", message);
        payload.put("parse_mode", "HTML");
        payload.put("disable_web_page_preview", true);
        
        String jsonPayload = objectMapper.writeValueAsString(payload);
        
        RequestBody body = RequestBody.create(jsonPayload, MediaType.parse("application/json"));
        
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String responseBody = response.body() != null ? response.body().string() : "No response body";
                throw new IOException("Telegram API error: " + response.code() + " - " + responseBody);
            }
            
            logger.debug("Telegram message sent successfully");
        }
    }
    
    public boolean isTelegramEnabled() {
        return telegramEnabled;
    }
    
    public void testTelegramConnection() {
        if (!telegramEnabled) {
            logger.warn("Telegram not configured - cannot test connection");
            return;
        }
        
        try {
            String testMessage = String.format(
                "🧪 TELEGRAM TEST\n" +
                "BTC Options Straddle Bot\n" +
                "Connection test successful!\n" +
                "Time: %s",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            );
            
            sendTelegramMessage(testMessage);
            logger.info("Telegram connection test successful");
            
        } catch (Exception e) {
            logger.error("Telegram connection test failed: {}", e.getMessage());
            telegramEnabled = false; // Disable if test fails
        }
    }
}