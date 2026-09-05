package com.activeage.payment.service;

import com.activeage.payment.model.PaymentIntent;
import com.activeage.payment.model.PaymentResult;
import com.activeage.payment.model.PaymentStatus;
import com.mercadopago.MercadoPagoConfig;
import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.client.preference.PreferenceItemRequest;
import com.mercadopago.client.preference.PreferenceRequest;
import com.mercadopago.resources.preference.Preference;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.resources.payment.Payment;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * Implementação do serviço de pagamentos específica para o Mercado Pago.
 *
 * Esta classe é responsável por interagir diretamente com o SDK oficial do Mercado Pago.
 * Ela gera links de checkout dinâmicos e também atua como roteador, notificando o
 * backend principal via chamadas HTTP quando um pagamento é confirmado.
 */
@Service
public class MercadoPagoPaymentService implements PaymentService {

    @Value("${mercadopago.access-token}")
    private String accessToken;

    @Value("${webhook.base-url}")
    private String webhookBaseUrl;

    @Value("${main-backend.url}")
    private String mainBackendUrl;

    /**
     * Inicializa as configurações do Mercado Pago após a injeção de dependências.
     *
     * Este método é executado automaticamente pelo Spring (@PostConstruct) para
     * configurar o Token de Acesso necessário nas chamadas da API do Mercado Pago.
     */
    @PostConstruct
    public void init() {
        MercadoPagoConfig.setAccessToken(accessToken);
    }

    /**
     * Cria uma nova preferência (Preference) de checkout no Mercado Pago.
     *
     * A preferência engloba o valor do item, uma referência externa para vínculo
     * e configura a URL dinâmica de notificação (webhook) para o ambiente correto.
     *
     * @param intent Objeto com os detalhes do pagamento e referência externa.
     * @return Um objeto com o ID da preferência e a URL (InitPoint) para redirecionar o usuário.
     * @throws RuntimeException Caso ocorra algum erro de comunicação com a API do Mercado Pago.
     */
    @Override
    public PaymentResult createPayment(PaymentIntent intent) {
        try {
            PreferenceClient client = new PreferenceClient();

            PreferenceItemRequest item = PreferenceItemRequest.builder()
                    .title(intent.description())
                    .quantity(1)
                    .unitPrice(intent.amount())
                    .build();

            PreferenceRequest request = PreferenceRequest.builder()
                    .items(Collections.singletonList(item))
                    .externalReference(intent.referenceId())
                    .notificationUrl(webhookBaseUrl + "/api/payments/webhook")
                    .build();

            Preference preference = client.create(request);

            return new PaymentResult(preference.getId(), preference.getInitPoint(), PaymentStatus.PENDING);

        } catch (Exception e) {
            throw new RuntimeException("Erro ao criar pagamento no Mercado Pago: " + e.getMessage());
        }
    }

    /**
     * Valida e roteia uma notificação de pagamento recebida via webhook.
     *
     * Este método não confia cegamente no ID recebido. Ele consulta ativamente a API
     * do Mercado Pago para buscar o status da transação. Se o pagamento for aprovado,
     * realiza um PUT HTTP no backend principal para ativar o agendamento ou a assinatura médica.
     *
     * @param paymentId O identificador oficial do pagamento no Mercado Pago.
     */
    @Override
    public void handleWebhook(String paymentId) {
        try {
            PaymentClient client = new PaymentClient();

            Payment payment = client.get(Long.parseLong(paymentId));
            String status = payment.getStatus();
            String referenceId = payment.getExternalReference();
            System.out.println("🔔 Webhook Recebido!");
            System.out.println("ID do Pagamento: " + paymentId);
            System.out.println("Referência (Agendamento/Médico): " + referenceId);
            System.out.println("Status oficial: " + status);
            if ("approved".equals(status)) {
                System.out.println("✅ Pagamento Aprovado! Avisando o backend principal...");

                String endpointTarget;

                if (referenceId.startsWith("MED-")) {
                    endpointTarget = "/api/usuarios/medicos/" + referenceId + "/assinatura/ativar";
                } else {
                    endpointTarget = "/api/agendamentos/" + referenceId + "/confirmar-pagamento";
                }
                java.net.http.HttpClient httpClient = java.net.http.HttpClient.newHttpClient();
                java.net.http.HttpRequest httpRequest = java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create(mainBackendUrl + endpointTarget))
                        .PUT(java.net.http.HttpRequest.BodyPublishers.noBody())
                        .build();
                httpClient.send(httpRequest, java.net.http.HttpResponse.BodyHandlers.ofString());
                System.out.println("🚀 Backend principal atualizado com sucesso! (Rota: " + endpointTarget + ")");
            }
        } catch (Exception e) {
            System.err.println("Erro ao processar webhook: " + e.getMessage());
        }
    }
}