package com.activeage.payment.model;

/**
 * Representa o resultado da criação de uma preferência de pagamento.
 *
 * Este objeto é devolvido ao solicitante logo após a comunicação com o gateway,
 * contendo o link seguro para onde o usuário deve ser redirecionado para efetuar a compra.
 *
 * @param paymentId O identificador único da preferência gerado pelo gateway.
 * @param checkoutUrl A URL (InitPoint) para redirecionamento do usuário ao fluxo de pagamento.
 * @param status O status inicial atribuído à transação recém-criada.
 */
public record PaymentResult(
        String paymentId,
        String checkoutUrl,
        PaymentStatus status
) {}