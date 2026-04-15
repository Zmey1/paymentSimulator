package com.payments.paymentservice.dto;

public record RazorpayOrderRequest(
        String customerName,
        String email,
        String contact,
        double amount
) {
}
