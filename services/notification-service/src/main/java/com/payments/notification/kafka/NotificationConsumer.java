package com.payments.notification.kafka;

import com.payments.notification.model.PaymentEvent;
import com.payments.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);
    private final NotificationService notificationService;

    public NotificationConsumer(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @KafkaListener(topics = {PaymentEvent.TOPIC_APPROVED, PaymentEvent.TOPIC_FLAGGED},
                   groupId = "notification-group")
    public void handlePaymentResult(PaymentEvent event) {
        log.info("Received payment result: paymentId={}, status={}", event.getPaymentId(), event.getStatus());
        notificationService.sendNotification(event);
    }
}
