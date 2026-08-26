package com.activeage.payment.model;

import java.math.BigDecimal;

public record PaymentIntent(
        BigDecimal amount,
        String description,
        String payerEmail,
        PaymentType type,
        String referenceId
) {
}