package com.payments.paymentservice.service;

import com.payments.paymentservice.kafka.PaymentProducer;
import com.payments.paymentservice.model.Payment;
import com.payments.paymentservice.model.PaymentEvent;
import com.payments.paymentservice.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentProducer paymentProducer;

    @InjectMocks
    private PaymentService paymentService;

    private Payment testPayment;

    @BeforeEach
    void setUp() {
        testPayment = new Payment();
        testPayment.setSender("Alice");
        testPayment.setReceiver("Bob");
        testPayment.setAmount(1000.0);
        testPayment.setPaymentType("TRANSFER");
    }

    @Test
    void createPayment_shouldSaveAndPublishEvent() {
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Payment result = paymentService.createPayment(testPayment);

        assertNotNull(result.getId());
        assertEquals("PENDING", result.getStatus());
        verify(paymentRepository).save(any(Payment.class));

        ArgumentCaptor<PaymentEvent> eventCaptor = ArgumentCaptor.forClass(PaymentEvent.class);
        verify(paymentProducer).sendPaymentCreated(eventCaptor.capture());
        PaymentEvent event = eventCaptor.getValue();
        assertEquals("Alice", event.getSender());
        assertEquals("Bob", event.getReceiver());
        assertEquals(1000.0, event.getAmount());
        assertEquals("PENDING", event.getStatus());
    }

    @Test
    void getAllPayments_shouldReturnAllPayments() {
        Payment p1 = new Payment();
        p1.setId("id1");
        Payment p2 = new Payment();
        p2.setId("id2");
        when(paymentRepository.findAll()).thenReturn(Arrays.asList(p1, p2));

        List<Payment> result = paymentService.getAllPayments();

        assertEquals(2, result.size());
        verify(paymentRepository).findAll();
    }

    @Test
    void getPaymentStats_shouldReturnAllCounts() {
        when(paymentRepository.count()).thenReturn(10L);
        when(paymentRepository.countByStatus("APPROVED")).thenReturn(7L);
        when(paymentRepository.countByStatus("FLAGGED")).thenReturn(2L);
        when(paymentRepository.countByStatus("PENDING")).thenReturn(1L);

        Map<String, Long> result = paymentService.getPaymentStats();

        Map<String, Long> expected = new LinkedHashMap<>();
        expected.put("total", 10L);
        expected.put("approved", 7L);
        expected.put("flagged", 2L);
        expected.put("pending", 1L);

        assertEquals(expected, result);
        verify(paymentRepository).count();
        verify(paymentRepository).countByStatus("APPROVED");
        verify(paymentRepository).countByStatus("FLAGGED");
        verify(paymentRepository).countByStatus("PENDING");
    }

    @Test
    void getPaymentById_shouldReturnPayment() {
        Payment p = new Payment();
        p.setId("test-id");
        when(paymentRepository.findById("test-id")).thenReturn(Optional.of(p));

        Payment result = paymentService.getPaymentById("test-id");

        assertNotNull(result);
        assertEquals("test-id", result.getId());
    }

    @Test
    void getPaymentById_shouldReturnNullWhenNotFound() {
        when(paymentRepository.findById("missing")).thenReturn(Optional.empty());

        Payment result = paymentService.getPaymentById("missing");

        assertNull(result);
    }
}
