package com.payments.frauddetection.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fraud.rules")
public class FraudRuleProperties {

    private double amountThreshold = 50000;
    private int velocityCount = 3;
    private long velocityWindowSeconds = 300;
    private boolean selfTransferEnabled = true;
    private double roundAmountMinimum = 30000;

    public double getAmountThreshold() {
        return amountThreshold;
    }

    public void setAmountThreshold(double amountThreshold) {
        this.amountThreshold = amountThreshold;
    }

    public int getVelocityCount() {
        return velocityCount;
    }

    public void setVelocityCount(int velocityCount) {
        this.velocityCount = velocityCount;
    }

    public long getVelocityWindowSeconds() {
        return velocityWindowSeconds;
    }

    public void setVelocityWindowSeconds(long velocityWindowSeconds) {
        this.velocityWindowSeconds = velocityWindowSeconds;
    }

    public boolean isSelfTransferEnabled() {
        return selfTransferEnabled;
    }

    public void setSelfTransferEnabled(boolean selfTransferEnabled) {
        this.selfTransferEnabled = selfTransferEnabled;
    }

    public double getRoundAmountMinimum() {
        return roundAmountMinimum;
    }

    public void setRoundAmountMinimum(double roundAmountMinimum) {
        this.roundAmountMinimum = roundAmountMinimum;
    }
}
