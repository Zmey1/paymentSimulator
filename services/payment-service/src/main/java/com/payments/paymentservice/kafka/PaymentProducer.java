package com.payments.paymentservice.kafka;

import com.payments.paymentservice.model.PaymentEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class PaymentProducer {

    private static final Logger log = LoggerFactory.getLogger(PaymentProducer.class);
    private final KafkaTemplate<String, PaymentEvent> kafkaTemplate;

    public PaymentProducer(KafkaTemplate<String, PaymentEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendPaymentCreated(PaymentEvent event) {
        log.info("Publishing payment-created event for paymentId={}", event.getPaymentId());
        kafkaTemplate.send(PaymentEvent.TOPIC_CREATED, event.getPaymentId(), event);
    }
}
