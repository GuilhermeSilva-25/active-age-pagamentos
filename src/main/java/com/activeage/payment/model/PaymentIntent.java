package com.activeage.payment.model;

import java.math.BigDecimal;

/**
 * Representa a intenção de criação de um novo pagamento.
 *
 * Este DTO (Data Transfer Object) encapsula as informações enviadas pelo frontend
 * ou backend principal necessárias para gerar uma cobrança no gateway,
 * como o valor da transação e o identificador de origem.
 *
 * @param amount O valor monetário da transação.
 * @param description A descrição do item ou serviço que será exibida no checkout.
 * @param payerEmail O endereço de e-mail do usuário que está realizando o pagamento.
 * @param type O tipo de serviço sendo pago (ex: consulta ou assinatura).
 * @param referenceId O identificador de origem (ID do médico ou do agendamento) usado para o roteamento do webhook.
 */
public record PaymentIntent(
        BigDecimal amount,
        String description,
        String payerEmail,
        PaymentType type,
        String referenceId
) {
}