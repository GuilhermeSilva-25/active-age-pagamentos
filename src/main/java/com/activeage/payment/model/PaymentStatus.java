package com.activeage.payment.model;

/**
 * Enumera os possíveis estados de uma transação de pagamento.
 *
 * Reflete o ciclo de vida e a situação atual do pagamento desde a sua criação
 * até a sua aprovação ou cancelamento pelo gateway.
 */
public enum PaymentStatus {
    PENDING,
    APPROVED,
    REJECTED,
    CANCELLED
}