package com.payments.frauddetection.kafka;

import com.payments.frauddetection.model.PaymentEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class FraudResultProducer {

    private static final Logger log = LoggerFactory.getLogger(FraudResultProducer.class);
    private final KafkaTemplate<String, PaymentEvent> kafkaTemplate;

    public FraudResultProducer(KafkaTemplate<String, PaymentEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendResult(PaymentEvent event) {
        String topic = "APPROVED".equals(event.getStatus())
                ? PaymentEvent.TOPIC_APPROVED
                : PaymentEvent.TOPIC_FLAGGED;

        log.info("Publishing fraud result: paymentId={}, status={}, topic={}",
                event.getPaymentId(), event.getStatus(), topic);
        kafkaTemplate.send(topic, event.getPaymentId(), event);
    }
}
