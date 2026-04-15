package com.payments.notification.service;

import com.payments.notification.model.PaymentEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class NotificationServiceTest {

    private final NotificationService notificationService = new NotificationService();

    @Test
    void sendNotification_shouldHandleApprovedPayment() {
        PaymentEvent event = new PaymentEvent(
                "id1", "Alice", "Bob", 1000.0, "TRANSFER", "APPROVED", System.currentTimeMillis()
        );

        assertDoesNotThrow(() -> notificationService.sendNotification(event));
    }

    @Test
    void sendNotification_shouldHandleFlaggedPayment() {
        PaymentEvent event = new PaymentEvent(
                "id2", "Alice", "Bob", 60000.0, "UPI", "FLAGGED", System.currentTimeMillis()
        );

        assertDoesNotThrow(() -> notificationService.sendNotification(event));
    }
}
