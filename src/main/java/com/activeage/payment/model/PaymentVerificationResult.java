package com.activeage.payment.model;

import java.math.BigDecimal;

/**
 * Representa o resultado da verificação de integridade e status de um pagamento.
 *
 * @param valid Indica se o pagamento foi confirmado como aprovado e com o valor correto.
 * @param status O status oficial do pagamento no Mercado Pago (ex: approved, pending, rejected).
 * @param amountPaid O valor efetivamente pago pelo usuário no Mercado Pago.
 * @param expectedAmount O valor que era esperado para a consulta/plano.
 * @param paymentId O ID oficial da transação de pagamento no Mercado Pago.
 * @param paymentMethod O método de pagamento utilizado (ex: account_money, credit_card, pix).
 * @param message Mensagem explicativa sobre o status da transação.
 */
public record PaymentVerificationResult(
        boolean valid,
        String status,
        BigDecimal amountPaid,
        BigDecimal expectedAmount,
        String paymentId,
        String paymentMethod,
        String message
) {}
