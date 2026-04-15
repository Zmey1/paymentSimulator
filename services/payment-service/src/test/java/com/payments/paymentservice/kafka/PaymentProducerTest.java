package com.payments.paymentservice.kafka;

import com.payments.paymentservice.model.PaymentEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentProducerTest {

    @Mock
    private KafkaTemplate<String, PaymentEvent> kafkaTemplate;

    @InjectMocks
    private PaymentProducer paymentProducer;

    @Test
    void sendPaymentCreated_shouldPublishToCorrectTopic() {
        PaymentEvent event = new PaymentEvent(
                "test-id", "Alice", "Bob", 1000.0, "TRANSFER", "PENDING", System.currentTimeMillis()
        );

        paymentProducer.sendPaymentCreated(event);

        verify(kafkaTemplate).send(PaymentEvent.TOPIC_CREATED, "test-id", event);
    }
}
