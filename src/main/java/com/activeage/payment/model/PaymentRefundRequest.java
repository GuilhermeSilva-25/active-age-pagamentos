package com.activeage.payment.model;

import java.math.BigDecimal;

/**
 * DTO para solicitação de reembolso de pagamento no gateway.
 *
 * @param paymentId ID da transação no gateway (ex: ID numérico do Mercado Pago ou com prefixo MP-).
 * @param amount    Valor a ser estornado (opcional, null ou zero indica reembolso total).
 * @param reason    Motivo do cancelamento/estorno.
 */
public record PaymentRefundRequest(
        String paymentId,
        BigDecimal amount,
        String reason
) {}
