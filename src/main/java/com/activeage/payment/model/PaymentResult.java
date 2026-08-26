package com.activeage.payment.model;

public record PaymentResult(
        String paymentId,
        String checkoutUrl,
        PaymentStatus status
) {}