package com.payments.paymentservice.service;

import com.payments.paymentservice.kafka.PaymentProducer;
import com.payments.paymentservice.model.Payment;
import com.payments.paymentservice.model.PaymentEvent;
import com.payments.paymentservice.repository.PaymentRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentProducer paymentProducer;

    public PaymentService(PaymentRepository paymentRepository, PaymentProducer paymentProducer) {
        this.paymentRepository = paymentRepository;
        this.paymentProducer = paymentProducer;
    }

    public Payment createPayment(Payment payment) {
        payment.setId(UUID.randomUUID().toString());
        payment.setStatus("PENDING");
        Payment saved = paymentRepository.save(payment);

        PaymentEvent event = new PaymentEvent(
                saved.getId(),
                saved.getSender(),
                saved.getReceiver(),
                saved.getAmount(),
                saved.getPaymentType(),
                saved.getStatus(),
                System.currentTimeMillis()
        );
        paymentProducer.sendPaymentCreated(event);

        return saved;
    }

    public List<Payment> getAllPayments() {
        return paymentRepository.findAll();
    }

    public Map<String, Long> getPaymentStats() {
        Map<String, Long> stats = new LinkedHashMap<>();
        stats.put("total", paymentRepository.count());
        stats.put("approved", paymentRepository.countByStatus("APPROVED"));
        stats.put("flagged", paymentRepository.countByStatus("FLAGGED"));
        stats.put("pending", paymentRepository.countByStatus("PENDING"));
        return stats;
    }

    public Payment getPaymentById(String id) {
        return paymentRepository.findById(id).orElse(null);
    }
}
