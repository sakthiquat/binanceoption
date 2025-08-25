package com.trading.bot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.bot.config.TradingConfig;
import com.trading.bot.model.*;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Service
public class OrderService {
    
    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);
    
    @Autowired
    private TradingConfig config;
    
    @Autowired
    private AuthenticationService authService;
    
    @Autowired
    private RetryHandler retryHandler;
    
    @Autowired
    private LoggingService loggingService;
    
    @Autowired
    private CircuitBreaker circuitBreaker;
    
    private OkHttpClient httpClient;
    private ObjectMapper objectMapper;
    
    @PostConstruct
    public void init() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        
        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules();
        
        logger.info("Order Service initialized");
    }
    
    public OrderResponse placeOrder(OrderRequest orderRequest) throws Exception {
        return retryHandler.executeWithRetry(() -> {
            try {
                String timestamp = String.valueOf(Instant.now().toEpochMilli());
                
                String queryString = authService.createQueryString(
                    "symbol", orderRequest.getSymbol(),
                    "side", orderRequest.getSide().name(),
                    "type", orderRequest.getType().name(),
                    "quantity", orderRequest.getQuantity().toPlainString(),
                    "price", orderRequest.getPrice().toPlainString(),
                    "timestamp", timestamp
                );
                
                String signature = authService.createSignature(queryString, config.getSecretKey());
                queryString += "&signature=" + signature;
                
                String url = config.getBinanceApiUrl() + "/eapi/v1/order";
                
                RequestBody body = RequestBody.create(queryString, MediaType.parse("application/x-www-form-urlencoded"));
                
                Request request = new Request.Builder()
                        .url(url)
                        .post(body)
                        .addHeader("X-MBX-APIKEY", config.getApiKey())
                        .addHeader("Content-Type", "application/x-www-form-urlencoded")
                        .build();
                
                try (Response response = httpClient.newCall(request).execute()) {
                    String responseBody = response.body().string();
                    
                    if (!response.isSuccessful()) {
                        logger.error("Order placement failed: {} - {}", response.code(), responseBody);
                        loggingService.logError("OrderService", "placeOrder", 
                            String.format("HTTP %d: %s", response.code(), responseBody), null);
                        throw new RuntimeException("Failed to place order: " + response.code() + " - " + responseBody);
                    }
                    
                    OrderResponse orderResponse = parseOrderResponse(responseBody);
                    
                    // Log order placement
                    loggingService.logOrderPlacement(orderRequest, orderResponse.getOrderId());
                    
                    // Log order fill if immediately filled
                    if (orderResponse.isFilled()) {
                        loggingService.logOrderFill(orderResponse);
                    }
                    
                    logger.info("Order placed successfully: {} {} {} @ {} (Order ID: {})", 
                               orderResponse.getSide(), 
                               orderResponse.getOriginalQuantity(), 
                               orderResponse.getSymbol(), 
                               orderResponse.getPrice(),
                               orderResponse.getOrderId());
                    
                    return orderResponse;
                }
            } catch (IOException e) {
                throw new RuntimeException("Error placing order", e);
            }
        }, "placeOrder");
    }
    
    public OrderResponse modifyOrder(OrderModifyRequest modifyRequest) throws Exception {
        return retryHandler.executeWithRetry(() -> {
            try {
                String timestamp = String.valueOf(Instant.now().toEpochMilli());
                
                String queryString = authService.createQueryString(
                    "symbol", modifyRequest.getSymbol(),
                    "orderId", modifyRequest.getOrderId(),
                    "quantity", modifyRequest.getQuantity().toPlainString(),
                    "price", modifyRequest.getPrice().toPlainString(),
                    "timestamp", timestamp
                );
                
                String signature = authService.createSignature(queryString, config.getSecretKey());
                queryString += "&signature=" + signature;
                
                String url = config.getBinanceApiUrl() + "/eapi/v1/order";
                
                RequestBody body = RequestBody.create(queryString, MediaType.parse("application/x-www-form-urlencoded"));
                
                Request request = new Request.Builder()
                        .url(url)
                        .post(body)  // Changed from PUT to POST for Binance Options API compatibility
                        .addHeader("X-MBX-APIKEY", config.getApiKey())
                        .addHeader("Content-Type", "application/x-www-form-urlencoded")
                        .build();
                
                try (Response response = httpClient.newCall(request).execute()) {
                    String responseBody = response.body().string();
                    
                    if (!response.isSuccessful()) {
                        logger.error("Order modification failed: {} - {}", response.code(), responseBody);
                        loggingService.logError("OrderService", "modifyOrder", 
                            String.format("HTTP %d: %s", response.code(), responseBody), null);
                        throw new RuntimeException("Failed to modify order: " + response.code() + " - " + responseBody);
                    }
                    
                    OrderResponse orderResponse = parseOrderResponse(responseBody);
                    logger.info("Order modified successfully: {} @ {} (Order ID: {})", 
                               orderResponse.getSymbol(), 
                               orderResponse.getPrice(),
                               orderResponse.getOrderId());
                    
                    // Log successful modification
                    loggingService.logTradingAction("ORDER_MODIFIED", 
                        String.format("OrderId: %s, Symbol: %s, NewPrice: %s", 
                            orderResponse.getOrderId(), orderResponse.getSymbol(), orderResponse.getPrice()));
                    
                    return orderResponse;
                }
            } catch (IOException e) {
                throw new RuntimeException("Error modifying order", e);
            }
        }, "modifyOrder");
    }
    
    public OrderResponse cancelOrder(String symbol, String orderId) throws Exception {
        return retryHandler.executeWithRetry(() -> {
            try {
                String timestamp = String.valueOf(Instant.now().toEpochMilli());
                
                String queryString = authService.createQueryString(
                    "symbol", symbol,
                    "orderId", orderId,
                    "timestamp", timestamp
                );
                
                String signature = authService.createSignature(queryString, config.getSecretKey());
                
                String url = config.getBinanceApiUrl() + "/eapi/v1/order?" + queryString + "&signature=" + signature;
                
                Request request = new Request.Builder()
                        .url(url)
                        .delete()
                        .addHeader("X-MBX-APIKEY", config.getApiKey())
                        .build();
                
                try (Response response = httpClient.newCall(request).execute()) {
                    String responseBody = response.body().string();
                    
                    if (!response.isSuccessful()) {
                        logger.error("Order cancellation failed: {} - {}", response.code(), responseBody);
                        loggingService.logError("OrderService", "cancelOrder", 
                            String.format("HTTP %d: %s", response.code(), responseBody), null);
                        throw new RuntimeException("Failed to cancel order: " + response.code() + " - " + responseBody);
                    }
                    
                    OrderResponse orderResponse = parseOrderResponse(responseBody);
                    logger.info("Order cancelled successfully: {} (Order ID: {})", 
                               orderResponse.getSymbol(), 
                               orderResponse.getOrderId());
                    
                    // Log successful cancellation
                    loggingService.logTradingAction("ORDER_CANCELLED", 
                        String.format("OrderId: %s, Symbol: %s", 
                            orderResponse.getOrderId(), orderResponse.getSymbol()));
                    
                    return orderResponse;
                }
            } catch (IOException e) {
                throw new RuntimeException("Error cancelling order", e);
            }
        }, "cancelOrder");
    }
    
    public OrderResponse getOrderStatus(String symbol, String orderId) throws Exception {
        return retryHandler.executeWithRetry(() -> {
            try {
                String timestamp = String.valueOf(Instant.now().toEpochMilli());
                
                String queryString = authService.createQueryString(
                    "symbol", symbol,
                    "orderId", orderId,
                    "timestamp", timestamp
                );
                
                String signature = authService.createSignature(queryString, config.getSecretKey());
                
                String url = config.getBinanceApiUrl() + "/eapi/v1/order?" + queryString + "&signature=" + signature;
                
                Request request = new Request.Builder()
                        .url(url)
                        .get()
                        .addHeader("X-MBX-APIKEY", config.getApiKey())
                        .build();
                
                try (Response response = httpClient.newCall(request).execute()) {
                    String responseBody = response.body().string();
                    
                    if (!response.isSuccessful()) {
                        logger.error("Get order status failed: {} - {}", response.code(), responseBody);
                        loggingService.logError("OrderService", "getOrderStatus", 
                            String.format("HTTP %d: %s", response.code(), responseBody), null);
                        throw new RuntimeException("Failed to get order status: " + response.code() + " - " + responseBody);
                    }
                    
                    return parseOrderResponse(responseBody);
                }
            } catch (IOException e) {
                throw new RuntimeException("Error getting order status", e);
            }
        }, "getOrderStatus");
    }
    
    private OrderResponse parseOrderResponse(String responseBody) throws IOException {
        JsonNode jsonNode = objectMapper.readTree(responseBody);
        
        OrderResponse response = new OrderResponse();
        response.setOrderId(jsonNode.get("orderId").asText());
        response.setSymbol(jsonNode.get("symbol").asText());
        response.setStatus(OrderStatus.valueOf(jsonNode.get("status").asText()));
        
        if (jsonNode.has("executedQty")) {
            response.setFilledQuantity(jsonNode.get("executedQty").decimalValue());
        }
        if (jsonNode.has("avgPrice")) {
            response.setAvgPrice(jsonNode.get("avgPrice").decimalValue());
        }
        if (jsonNode.has("side")) {
            response.setSide(OrderSide.valueOf(jsonNode.get("side").asText()));
        }
        if (jsonNode.has("origQty")) {
            response.setOriginalQuantity(jsonNode.get("origQty").decimalValue());
        }
        if (jsonNode.has("price")) {
            response.setPrice(jsonNode.get("price").decimalValue());
        }
        
        return response;
    }
}