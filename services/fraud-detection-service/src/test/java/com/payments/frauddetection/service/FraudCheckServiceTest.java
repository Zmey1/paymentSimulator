package com.payments.frauddetection.service;

import com.payments.frauddetection.config.FraudRuleProperties;
import com.payments.frauddetection.model.PaymentEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FraudCheckServiceTest {

    private FraudCheckService fraudCheckService;

    @BeforeEach
    void setUp() {
        FraudRuleProperties fraudRuleProperties = new FraudRuleProperties();
        fraudRuleProperties.setAmountThreshold(50000.0);
        fraudRuleProperties.setVelocityCount(3);
        fraudRuleProperties.setVelocityWindowSeconds(300L);
        fraudRuleProperties.setSelfTransferEnabled(true);
        fraudRuleProperties.setRoundAmountMinimum(30000.0);
        fraudCheckService = new FraudCheckService(fraudRuleProperties);
    }

    @Test
    void checkFraud_shouldFlagHighAmount() {
        assertEquals("FLAGGED", fraudCheckService.checkFraud(event("id1", "Alice", "Bob", 60000.0, 1_000L)));
    }

    @Test
    void checkFraud_shouldApproveNormalAmount() {
        assertEquals("APPROVED", fraudCheckService.checkFraud(event("id2", "Alice", "Bob", 10000.0, 1_000L)));
    }

    @Test
    void checkFraud_shouldFlagSelfTransfer() {
        assertEquals("FLAGGED", fraudCheckService.checkFraud(event("id3", "Alice", "Alice", 10000.0, 2_000L)));
    }

    @Test
    void checkFraud_shouldFlagRoundAmountAboveMinimum() {
        assertEquals("FLAGGED", fraudCheckService.checkFraud(event("id4", "Alice", "Bob", 40000.0, 3_000L)));
    }

    @Test
    void checkFraud_shouldApproveRoundAmountBelowMinimum() {
        assertEquals("APPROVED", fraudCheckService.checkFraud(event("id5", "Alice", "Bob", 20000.0, 4_000L)));
    }

    @Test
    void checkFraud_shouldFlagVelocityBurst() {
        assertEquals("APPROVED", fraudCheckService.checkFraud(event("id6", "Rahul", "Bob", 12500.0, 10_000L)));
        assertEquals("APPROVED", fraudCheckService.checkFraud(event("id7", "Rahul", "Bob", 12500.0, 11_000L)));
        assertEquals("FLAGGED", fraudCheckService.checkFraud(event("id8", "Rahul", "Bob", 12500.0, 12_000L)));
    }

    @Test
    void checkFraud_shouldApproveVelocityWhenSpreadOut() {
        assertEquals("APPROVED", fraudCheckService.checkFraud(event("id9", "Neha", "Bob", 12500.0, 1_000L)));
        assertEquals("APPROVED", fraudCheckService.checkFraud(event("id10", "Neha", "Bob", 12500.0, 302_000L)));
        assertEquals("APPROVED", fraudCheckService.checkFraud(event("id11", "Neha", "Bob", 12500.0, 603_000L)));
    }

    private PaymentEvent event(String id, String sender, String receiver, double amount, long timestamp) {
        return new PaymentEvent(id, sender, receiver, amount, "TRANSFER", "PENDING", timestamp);
    }
}
