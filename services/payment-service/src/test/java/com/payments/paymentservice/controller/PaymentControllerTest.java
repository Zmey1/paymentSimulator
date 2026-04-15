package com.payments.paymentservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payments.paymentservice.dto.RazorpayCheckoutStatusResponse;
import com.payments.paymentservice.dto.RazorpayOrderResponse;
import com.payments.paymentservice.model.Payment;
import com.payments.paymentservice.service.RazorpayGatewayService;
import com.payments.paymentservice.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PaymentController.class)
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaymentService paymentService;

    @MockBean
    private RazorpayGatewayService razorpayGatewayService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createPayment_shouldReturn201() throws Exception {
        Payment payment = new Payment();
        payment.setId("test-id");
        payment.setSender("Alice");
        payment.setReceiver("Bob");
        payment.setAmount(500.0);
        payment.setPaymentType("TRANSFER");
        payment.setStatus("PENDING");

        when(paymentService.createPayment(any(Payment.class))).thenReturn(payment);

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payment)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("test-id"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void getAllPayments_shouldReturnList() throws Exception {
        Payment p = new Payment();
        p.setId("id1");
        p.setSender("Alice");
        p.setReceiver("Bob");
        p.setAmount(100.0);
        p.setStatus("APPROVED");
        when(paymentService.getAllPayments()).thenReturn(Arrays.asList(p));

        mockMvc.perform(get("/api/payments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("id1"));
    }

    @Test
    void getAllPayments_shouldReturnEmptyList() throws Exception {
        when(paymentService.getAllPayments()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/payments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getPaymentStats_shouldReturnStats() throws Exception {
        Map<String, Long> stats = new LinkedHashMap<>();
        stats.put("total", 12L);
        stats.put("approved", 8L);
        stats.put("flagged", 3L);
        stats.put("pending", 1L);
        when(paymentService.getPaymentStats()).thenReturn(stats);

        mockMvc.perform(get("/api/payments/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(12))
                .andExpect(jsonPath("$.approved").value(8))
                .andExpect(jsonPath("$.flagged").value(3))
                .andExpect(jsonPath("$.pending").value(1));
    }

    @Test
    void getRazorpayConfig_shouldReturnGatewayStatus() throws Exception {
        when(razorpayGatewayService.getCheckoutStatus()).thenReturn(
                new RazorpayCheckoutStatusResponse(true, "rzp_test_123", "PayFlow Demo", "Sandbox Checkout", "Demo Merchant")
        );

        mockMvc.perform(get("/api/payments/razorpay/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.keyId").value("rzp_test_123"));
    }

    @Test
    void createRazorpayOrder_shouldReturnOrderPayload() throws Exception {
        when(razorpayGatewayService.createOrder(any())).thenReturn(
                new RazorpayOrderResponse(
                        "rzp_test_123",
                        "order_123",
                        50000L,
                        "INR",
                        "PayFlow Demo",
                        "Sandbox Checkout",
                        "Alice",
                        "alice@example.com",
                        "9999999999"
                )
        );

        mockMvc.perform(post("/api/payments/razorpay/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerName": "Alice",
                                  "email": "alice@example.com",
                                  "contact": "9999999999",
                                  "amount": 500
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("order_123"))
                .andExpect(jsonPath("$.amount").value(50000));
    }

    @Test
    void verifyRazorpayPayment_shouldReturnCreatedPayment() throws Exception {
        Payment payment = new Payment();
        payment.setId("razorpay-demo-id");
        payment.setSender("Alice");
        payment.setReceiver("Demo Merchant");
        payment.setAmount(500.0);
        payment.setPaymentType("UPI");
        payment.setStatus("PENDING");

        when(razorpayGatewayService.verifyAndCreatePayment(any())).thenReturn(payment);

        mockMvc.perform(post("/api/payments/razorpay/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "razorpayOrderId": "order_123",
                                  "razorpayPaymentId": "pay_123",
                                  "razorpaySignature": "sig_123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("razorpay-demo-id"))
                .andExpect(jsonPath("$.receiver").value("Demo Merchant"));
    }

    @Test
    void getPaymentById_shouldReturn404WhenNotFound() throws Exception {
        when(paymentService.getPaymentById("missing")).thenReturn(null);

        mockMvc.perform(get("/api/payments/missing"))
                .andExpect(status().isNotFound());
    }
}
