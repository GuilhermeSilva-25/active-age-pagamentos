package com.activeage.payment.service;

import com.activeage.payment.model.PaymentIntent;
import com.activeage.payment.model.PaymentResult;

/**
 * Contrato padrão para a implementação de serviços de pagamento.
 * <p>
 * Define os métodos essenciais que qualquer integração de gateway de pagamento
 * deve implementar, garantindo o desacoplamento do controlador.
 */
public interface PaymentService {
    /**
     * Gera uma nova preferência de pagamento no gateway.
     *
     * @param intent Objeto com os detalhes do pagamento (valor, descrição, referência).
     * @return O resultado contendo as informações geradas, como o ID e a URL do checkout.
     */
    PaymentResult createPayment(PaymentIntent intent);

    /**
     * Processa um evento de atualização de status de pagamento.
     * <p>
     * Este método é chamado de forma assíncrona. Ele deve buscar o status real
     * da transação pelo ID e realizar ações em cascata, como avisar o backend principal.
     *
     * @param paymentId O identificador único do pagamento recebido no webhook.
     */
    void handleWebhook(String paymentId);

    /**
     * Verifica com o Mercado Pago se o pagamento foi realmente aprovado e se o valor pago é o correto.
     *
     * @param referenceId Referência externa (ex: AGEND-123 ou MED-456).
     * @param paymentId ID da transação no Mercado Pago (opcional se fornecido referenceId).
     * @param expectedAmount Valor esperado para a transação.
     * @return Objeto contendo o resultado detalhado da verificação de segurança.
     */
    com.activeage.payment.model.PaymentVerificationResult verifyPayment(String referenceId, String paymentId, java.math.BigDecimal expectedAmount);
}