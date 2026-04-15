package com.payments.notification.service;

import com.payments.notification.model.PaymentEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    public void sendNotification(PaymentEvent event) {
        log.info("NOTIFICATION: [EMAIL] Payment {} - Amount {} from {} to {} - Status: {}",
                event.getPaymentId(), event.getAmount(),
                event.getSender(), event.getReceiver(), event.getStatus());

        log.info("NOTIFICATION: [SMS] Payment {} - Status: {}",
                event.getPaymentId(), event.getStatus());
    }
}
