package com.activeage.payment.model;

import java.util.Map;

/**
 * Estrutura que mapeia a notificação assíncrona (IPN) enviada pelo Mercado Pago.
 *
 * Recebe o payload do webhook informando que houve uma alteração de status
 * em algum recurso, permitindo que o sistema atue em resposta a esse evento.
 *
 * @param action A ação específica que disparou a notificação (ex: "payment.updated").
 * @param type O tipo do recurso que sofreu alteração (geralmente "payment").
 * @param data Os dados complementares do evento, contendo chaves como o "id" do pagamento afetado.
 */
public record WebhookNotification(
        String action,
        String type,
        Map<String, String> data
) {
}