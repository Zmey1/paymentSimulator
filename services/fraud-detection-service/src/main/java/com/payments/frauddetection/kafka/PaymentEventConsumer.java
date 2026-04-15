package com.payments.frauddetection.kafka;

import com.payments.frauddetection.model.PaymentEvent;
import com.payments.frauddetection.service.FraudCheckService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventConsumer.class);
    private final FraudCheckService fraudCheckService;
    private final FraudResultProducer fraudResultProducer;

    public PaymentEventConsumer(FraudCheckService fraudCheckService, FraudResultProducer fraudResultProducer) {
        this.fraudCheckService = fraudCheckService;
        this.fraudResultProducer = fraudResultProducer;
    }

    @KafkaListener(topics = PaymentEvent.TOPIC_CREATED, groupId = "fraud-detection-group")
    public void handlePaymentCreated(PaymentEvent event) {
        log.info("Received payment-created event: paymentId={}, amount={}",
                event.getPaymentId(), event.getAmount());

        String result = fraudCheckService.checkFraud(event);
        event.setStatus(result);
        event.setTimestamp(System.currentTimeMillis());

        fraudResultProducer.sendResult(event);
    }
}
