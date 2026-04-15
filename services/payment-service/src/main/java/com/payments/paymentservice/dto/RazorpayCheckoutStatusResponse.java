package com.payments.paymentservice.dto;

public record RazorpayCheckoutStatusResponse(
        boolean enabled,
        String keyId,
        String merchantName,
        String description,
        String receiverName
) {
}
