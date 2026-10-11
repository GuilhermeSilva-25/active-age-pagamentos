package com.activeage.payment.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO com o resultado da operação de estorno/reembolso no gateway de pagamento.
 *
 * @param success    Indica se o reembolso foi aceito e concluído com sucesso.
 * @param status     Status da operação retornado pelo gateway (ex: "approved", "refunded", "failed").
 * @param refundId   Identificador do estorno gerado no gateway de pagamento.
 * @param paymentId  Identificador do pagamento original.
 * @param amount     Valor total estornado.
 * @param refundDate Data e hora da realização do estorno.
 * @param message    Mensagem detalhada descritiva do resultado da operação.
 */
public record PaymentRefundResult(
        boolean success,
        String status,
        String refundId,
        String paymentId,
        BigDecimal amount,
        LocalDateTime refundDate,
        String message
) {}

