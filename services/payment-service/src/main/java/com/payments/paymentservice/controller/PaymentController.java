package com.payments.paymentservice.controller;

import com.payments.paymentservice.dto.RazorpayCheckoutStatusResponse;
import com.payments.paymentservice.dto.RazorpayOrderRequest;
import com.payments.paymentservice.dto.RazorpayOrderResponse;
import com.payments.paymentservice.dto.RazorpayVerifyRequest;
import com.payments.paymentservice.model.Payment;
import com.payments.paymentservice.service.RazorpayGatewayService;
import com.payments.paymentservice.service.PaymentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final RazorpayGatewayService razorpayGatewayService;

    public PaymentController(PaymentService paymentService, RazorpayGatewayService razorpayGatewayService) {
        this.paymentService = paymentService;
        this.razorpayGatewayService = razorpayGatewayService;
    }

    @PostMapping
    public ResponseEntity<Payment> createPayment(@RequestBody Payment payment) {
        Payment created = paymentService.createPayment(payment);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<List<Payment>> getAllPayments() {
        return ResponseEntity.ok(paymentService.getAllPayments());
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> getPaymentStats() {
        return ResponseEntity.ok(paymentService.getPaymentStats());
    }

    @GetMapping("/razorpay/config")
    public ResponseEntity<RazorpayCheckoutStatusResponse> getRazorpayConfig() {
        return ResponseEntity.ok(razorpayGatewayService.getCheckoutStatus());
    }

    @PostMapping("/razorpay/orders")
    public ResponseEntity<RazorpayOrderResponse> createRazorpayOrder(@RequestBody RazorpayOrderRequest request) {
        return ResponseEntity.ok(razorpayGatewayService.createOrder(request));
    }

    @PostMapping("/razorpay/verify")
    public ResponseEntity<Payment> verifyRazorpayPayment(@RequestBody RazorpayVerifyRequest request) {
        return ResponseEntity.ok(razorpayGatewayService.verifyAndCreatePayment(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Payment> getPaymentById(@PathVariable String id) {
        Payment payment = paymentService.getPaymentById(id);
        if (payment == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(payment);
    }
}
