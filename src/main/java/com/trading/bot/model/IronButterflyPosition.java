package com.trading.bot.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public class IronButterflyPosition {
    private String positionId;
    private LocalDateTime creationTime;
    private BigDecimal atmStrike;
    private OptionLeg sellCall;
    private OptionLeg sellPut;
    private OptionLeg buyCall;
    private OptionLeg buyPut;
    private BigDecimal maxTheoreticalLoss;
    private PositionStatus status;

    public IronButterflyPosition() {
        this.positionId = UUID.randomUUID().toString();
        this.creationTime = LocalDateTime.now();
        this.status = PositionStatus.OPEN;
    }

    public IronButterflyPosition(BigDecimal atmStrike, OptionLeg sellCall, OptionLeg sellPut, 
                                OptionLeg buyCall, OptionLeg buyPut) {
        this();
        this.atmStrike = atmStrike;
        this.sellCall = sellCall;
        this.sellPut = sellPut;
        this.buyCall = buyCall;
        this.buyPut = buyPut;
        this.maxTheoreticalLoss = calculateMaxTheoreticalLoss();
    }

    // Getters and Setters
    public String getPositionId() { return positionId; }
    public void setPositionId(String positionId) { this.positionId = positionId; }

    public LocalDateTime getCreationTime() { return creationTime; }
    public void setCreationTime(LocalDateTime creationTime) { this.creationTime = creationTime; }

    public BigDecimal getAtmStrike() { return atmStrike; }
    public void setAtmStrike(BigDecimal atmStrike) { this.atmStrike = atmStrike; }

    public OptionLeg getSellCall() { return sellCall; }
    public void setSellCall(OptionLeg sellCall) { this.sellCall = sellCall; }

    public OptionLeg getSellPut() { return sellPut; }
    public void setSellPut(OptionLeg sellPut) { this.sellPut = sellPut; }

    public OptionLeg getBuyCall() { return buyCall; }
    public void setBuyCall(OptionLeg buyCall) { this.buyCall = buyCall; }

    public OptionLeg getBuyPut() { return buyPut; }
    public void setBuyPut(OptionLeg buyPut) { this.buyPut = buyPut; }

    public BigDecimal getMaxTheoreticalLoss() { return maxTheoreticalLoss; }
    public void setMaxTheoreticalLoss(BigDecimal maxTheoreticalLoss) { this.maxTheoreticalLoss = maxTheoreticalLoss; }

    public PositionStatus getStatus() { return status; }
    public void setStatus(PositionStatus status) { this.status = status; }

    public BigDecimal calculateCurrentPnL() {
        BigDecimal totalPnL = BigDecimal.ZERO;
        
        if (sellCall != null) {
            totalPnL = totalPnL.add(sellCall.calculatePnL());
        }
        if (sellPut != null) {
            totalPnL = totalPnL.add(sellPut.calculatePnL());
        }
        if (buyCall != null) {
            totalPnL = totalPnL.add(buyCall.calculatePnL());
        }
        if (buyPut != null) {
            totalPnL = totalPnL.add(buyPut.calculatePnL());
        }
        
        return totalPnL;
    }

    public BigDecimal calculateMaxTheoreticalLoss() {
        if (sellCall == null || buyCall == null) {
            return BigDecimal.ZERO;
        }
        
        // Max loss = Strike distance - Net premium received
        BigDecimal strikeDistance = buyCall.getStrike().subtract(atmStrike);
        BigDecimal netPremium = calculateNetPremiumReceived();
        
        return strikeDistance.subtract(netPremium).multiply(sellCall.getQuantity());
    }

    private BigDecimal calculateNetPremiumReceived() {
        BigDecimal premiumReceived = BigDecimal.ZERO;
        BigDecimal premiumPaid = BigDecimal.ZERO;
        
        if (sellCall != null && sellCall.getEntryPrice() != null) {
            premiumReceived = premiumReceived.add(sellCall.getEntryPrice().multiply(sellCall.getQuantity()));
        }
        if (sellPut != null && sellPut.getEntryPrice() != null) {
            premiumReceived = premiumReceived.add(sellPut.getEntryPrice().multiply(sellPut.getQuantity()));
        }
        if (buyCall != null && buyCall.getEntryPrice() != null) {
            premiumPaid = premiumPaid.add(buyCall.getEntryPrice().multiply(buyCall.getQuantity()));
        }
        if (buyPut != null && buyPut.getEntryPrice() != null) {
            premiumPaid = premiumPaid.add(buyPut.getEntryPrice().multiply(buyPut.getQuantity()));
        }
        
        return premiumReceived.subtract(premiumPaid);
    }

    public boolean isOpen() {
        return status == PositionStatus.OPEN;
    }
}