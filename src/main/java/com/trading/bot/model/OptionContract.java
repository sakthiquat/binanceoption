package com.trading.bot.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;

public class OptionContract {
    @JsonProperty("symbol")
    private String symbol;
    
    @JsonProperty("strikePrice")
    private BigDecimal strike;
    
    @JsonProperty("expiryDate")
    private LocalDate expiry;
    
    @JsonProperty("bidPrice")
    private BigDecimal bidPrice;
    
    @JsonProperty("askPrice")
    private BigDecimal askPrice;
    
    @JsonProperty("bidQty")
    private BigDecimal bidQuantity;
    
    @JsonProperty("askQty")
    private BigDecimal askQuantity;
    
    private OptionType type;

    public OptionContract() {}

    public OptionContract(String symbol, BigDecimal strike, LocalDate expiry, OptionType type) {
        this.symbol = symbol;
        this.strike = strike;
        this.expiry = expiry;
        this.type = type;
    }

    // Getters and Setters
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public BigDecimal getStrike() { return strike; }
    public void setStrike(BigDecimal strike) { this.strike = strike; }

    public LocalDate getExpiry() { return expiry; }
    public void setExpiry(LocalDate expiry) { this.expiry = expiry; }

    public OptionType getType() { return type; }
    public void setType(OptionType type) { this.type = type; }

    public BigDecimal getBidPrice() { return bidPrice; }
    public void setBidPrice(BigDecimal bidPrice) { this.bidPrice = bidPrice; }

    public BigDecimal getAskPrice() { return askPrice; }
    public void setAskPrice(BigDecimal askPrice) { this.askPrice = askPrice; }

    public BigDecimal getBidQuantity() { return bidQuantity; }
    public void setBidQuantity(BigDecimal bidQuantity) { this.bidQuantity = bidQuantity; }

    public BigDecimal getAskQuantity() { return askQuantity; }
    public void setAskQuantity(BigDecimal askQuantity) { this.askQuantity = askQuantity; }

    public boolean isCall() {
        return type == OptionType.CALL;
    }

    public boolean isPut() {
        return type == OptionType.PUT;
    }
}