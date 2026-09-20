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

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Locale;

@Service
public class MercadoPagoPaymentService implements PaymentService {

    @Value("${mercadopago.access-token}")
    private String accessToken;

    @Value("${webhook.base-url}")
    private String webhookBaseUrl;

    @Value("${main-backend.url}")
    private String mainBackendUrl;

    @PostConstruct
    public void init() {
        MercadoPagoConfig.setAccessToken(accessToken);
    }

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

    @Override
    public void handleWebhook(String paymentId) {
        try {
            PaymentClient client = new PaymentClient();

            Payment payment = client.get(Long.parseLong(paymentId));
            String status = payment.getStatus();
            String referenceId = payment.getExternalReference();
            System.out.println("Webhook Recebido!");
            System.out.println("ID do Pagamento: " + paymentId);
            System.out.println("Referencia: " + referenceId);
            System.out.println("Status oficial: " + status);
            if ("approved".equals(status)) {
                System.out.println("Pagamento Aprovado! Notificando backend...");

                String endpointTarget;
                HttpClient httpClient = HttpClient.newHttpClient();
                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder();

                if (referenceId != null && referenceId.startsWith("MED-")) {
                    endpointTarget = "/api/usuarios/medicos/" + referenceId + "/assinatura/ativar";
                    requestBuilder.uri(URI.create(mainBackendUrl + endpointTarget))
                            .PUT(HttpRequest.BodyPublishers.noBody());
                } else {
                    String cleanRef = referenceId != null ? referenceId.replace("AGEND-", "") : "";
                    endpointTarget = "/api/agendamentos/" + cleanRef + "/confirmar-pagamento";

                    BigDecimal valorPago = payment.getTransactionAmount();
                    String metodo = payment.getPaymentMethodId();
                    String transacaoId = payment.getId() != null ? payment.getId().toString() : paymentId;
                    String dataPagamentoStr = payment.getDateApproved() != null
                            ? payment.getDateApproved().toLocalDateTime().toString()
                            : LocalDateTime.now().toString();

                    double valDouble = valorPago != null ? valorPago.doubleValue() : 0.0;
                    String jsonBody = String.format(
                            Locale.US,
                            "{\"transacaoId\":\"%s\",\"metodo\":\"%s\",\"valorPago\":%.2f,\"dataPagamento\":\"%s\"}",
                            transacaoId,
                            metodo != null ? metodo : "mercado_pago",
                            valDouble,
                            dataPagamentoStr
                    );

                    requestBuilder.uri(URI.create(mainBackendUrl + endpointTarget))
                            .header("Content-Type", "application/json")
                            .PUT(HttpRequest.BodyPublishers.ofString(jsonBody));
                }

                HttpRequest httpRequest = requestBuilder.build();
                httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
                System.out.println("Backend principal atualizado com sucesso! (Rota: " + endpointTarget + ")");
            }
        } catch (Exception e) {
            System.err.println("Erro ao processar webhook: " + e.getMessage());
        }
    }
}