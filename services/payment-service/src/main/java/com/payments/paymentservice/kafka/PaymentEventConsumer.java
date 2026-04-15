package com.payments.paymentservice.kafka;

import com.payments.paymentservice.model.Payment;
import com.payments.paymentservice.model.PaymentEvent;
import com.payments.paymentservice.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Component
public class PaymentEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventConsumer.class);
    private final PaymentRepository paymentRepository;

    public PaymentEventConsumer(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @KafkaListener(topics = {PaymentEvent.TOPIC_APPROVED, PaymentEvent.TOPIC_FLAGGED},
                   groupId = "payment-service-group")
    @Transactional
    public void handlePaymentResult(PaymentEvent event) {
        log.info("Received payment result: paymentId={}, status={}", event.getPaymentId(), event.getStatus());

        Optional<Payment> optionalPayment = paymentRepository.findById(event.getPaymentId());
        if (optionalPayment.isEmpty()) {
            log.warn("Payment not found: paymentId={}", event.getPaymentId());
            return;
        }

        Payment payment = optionalPayment.get();
        if (!"PENDING".equals(payment.getStatus())) {
            log.warn("Payment already processed: paymentId={}, currentStatus={}",
                    event.getPaymentId(), payment.getStatus());
            return;
        }

        payment.setStatus(event.getStatus());
        payment.setUpdatedAt(LocalDateTime.now());
        paymentRepository.save(payment);
        log.info("Updated payment status: paymentId={}, newStatus={}", event.getPaymentId(), event.getStatus());
    }
}
