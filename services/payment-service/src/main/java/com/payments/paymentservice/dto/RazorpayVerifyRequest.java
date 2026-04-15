package com.payments.paymentservice.dto;

public record RazorpayVerifyRequest(
        String razorpayOrderId,
        String razorpayPaymentId,
        String razorpaySignature
) {
}
