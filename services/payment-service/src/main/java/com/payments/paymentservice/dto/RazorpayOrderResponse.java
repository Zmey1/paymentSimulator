package com.payments.paymentservice.dto;

public record RazorpayOrderResponse(
        String keyId,
        String orderId,
        long amount,
        String currency,
        String merchantName,
        String description,
        String customerName,
        String email,
        String contact
) {
}
