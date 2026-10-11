package com.activeage.payment.service;

import com.activeage.payment.model.PaymentIntent;
import com.activeage.payment.model.PaymentResult;
import com.activeage.payment.model.PaymentStatus;
import com.mercadopago.MercadoPagoConfig;
import com.mercadopago.client.preference.PreferenceBackUrlsRequest;
import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.client.preference.PreferenceItemRequest;
import com.mercadopago.client.preference.PreferenceRequest;
import com.mercadopago.resources.preference.Preference;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.client.payment.PaymentRefundClient;
import com.mercadopago.resources.payment.Payment;
import com.mercadopago.resources.payment.PaymentRefund;
import com.activeage.payment.model.PaymentRefundResult;
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

    @Value("${frontend.base-url:http://localhost:5173}")
    private String frontendBaseUrl;

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

            PreferenceBackUrlsRequest backUrls = PreferenceBackUrlsRequest.builder()
                    .success(frontendBaseUrl + "/dashboard?status=approved")
                    .failure(frontendBaseUrl + "/dashboard?status=failure")
                    .pending(frontendBaseUrl + "/dashboard?status=pending")
                    .build();

            PreferenceRequest request = PreferenceRequest.builder()
                    .items(Collections.singletonList(item))
                    .externalReference(intent.referenceId())
                    .notificationUrl(webhookBaseUrl + "/api/payments/webhook")
                    .backUrls(backUrls)
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
            System.out.println("Webhook Recebido! ID: " + paymentId + " | Ref: " + referenceId + " | Status: " + status);

            if ("approved".equals(status)) {
                notificarBackendAprovado(
                        payment.getId() != null ? payment.getId().toString() : paymentId,
                        referenceId,
                        payment.getTransactionAmount(),
                        payment.getPaymentMethodId(),
                        payment.getDateApproved() != null ? payment.getDateApproved().toLocalDateTime().toString() : LocalDateTime.now().toString()
                );
            }
        } catch (Exception e) {
            System.err.println("Erro ao processar webhook: " + e.getMessage());
        }
    }

    @Override
    public com.activeage.payment.model.PaymentVerificationResult verifyPayment(String referenceId, String paymentId, BigDecimal expectedAmount) {
        System.out.println("Consultando verificação de segurança no Mercado Pago...");
        System.out.println("Parâmetros: Ref=" + referenceId + " | PaymentId=" + paymentId + " | Valor Esperado=" + expectedAmount);

        // 1. Tentar busca direta por Payment ID caso fornecido
        if (paymentId != null && !paymentId.isBlank()) {
            try {
                String cleanPaymentId = paymentId.replaceAll("[^0-9]", "");
                if (!cleanPaymentId.isBlank()) {
                    PaymentClient client = new PaymentClient();
                    Payment payment = client.get(Long.parseLong(cleanPaymentId));
                    if (payment != null) {
                        String status = payment.getStatus();
                        BigDecimal amountPaid = payment.getTransactionAmount();
                        String method = payment.getPaymentMethodId();
                        String ref = payment.getExternalReference();

                        boolean isApproved = "approved".equalsIgnoreCase(status);
                        boolean isAmountCorrect = expectedAmount == null || (amountPaid != null && amountPaid.compareTo(expectedAmount) >= 0);

                        if (isApproved && isAmountCorrect) {
                            notificarBackendAprovado(
                                    cleanPaymentId,
                                    ref != null ? ref : referenceId,
                                    amountPaid,
                                    method,
                                    payment.getDateApproved() != null ? payment.getDateApproved().toLocalDateTime().toString() : LocalDateTime.now().toString()
                            );
                            return new com.activeage.payment.model.PaymentVerificationResult(
                                    true,
                                    status,
                                    amountPaid,
                                    expectedAmount,
                                    cleanPaymentId,
                                    method,
                                    "Pagamento validado e aprovado com sucesso no Mercado Pago."
                            );
                        } else if (!isApproved) {
                            return new com.activeage.payment.model.PaymentVerificationResult(
                                    false,
                                    status,
                                    amountPaid,
                                    expectedAmount,
                                    cleanPaymentId,
                                    method,
                                    "Pagamento encontrado, mas o status no Mercado Pago é: " + status
                            );
                        } else {
                            return new com.activeage.payment.model.PaymentVerificationResult(
                                    false,
                                    status,
                                    amountPaid,
                                    expectedAmount,
                                    cleanPaymentId,
                                    method,
                                    "O valor pago (R$ " + amountPaid + ") é inferior ao valor da consulta (R$ " + expectedAmount + ")."
                            );
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Aviso: Falha ao buscar por ID (" + paymentId + "): " + e.getMessage());
            }
        }

        // 2. Tentar busca por External Reference na API oficial do Mercado Pago
        if (referenceId != null && !referenceId.isBlank()) {
            try {
                HttpClient httpClient = HttpClient.newHttpClient();
                String encodedRef = java.net.URLEncoder.encode(referenceId, java.nio.charset.StandardCharsets.UTF_8);
                String searchUrl = "https://api.mercadopago.com/v1/payments/search?external_reference=" + encodedRef + "&sort=date_created&criteria=desc";

                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(searchUrl))
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Content-Type", "application/json")
                        .GET()
                        .build();

                HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(resp.body()).getAsJsonObject();
                    if (root.has("results") && root.get("results").isJsonArray()) {
                        com.google.gson.JsonArray results = root.getAsJsonArray("results");

                        for (com.google.gson.JsonElement el : results) {
                            com.google.gson.JsonObject pNode = el.getAsJsonObject();
                            String status = pNode.has("status") && !pNode.get("status").isJsonNull()
                                    ? pNode.get("status").getAsString()
                                    : "";
                            BigDecimal amountPaid = pNode.has("transaction_amount") && !pNode.get("transaction_amount").isJsonNull()
                                    ? pNode.get("transaction_amount").getAsBigDecimal()
                                    : BigDecimal.ZERO;
                            String pId = pNode.has("id") && !pNode.get("id").isJsonNull()
                                    ? pNode.get("id").getAsString()
                                    : "";
                            String method = pNode.has("payment_method_id") && !pNode.get("payment_method_id").isJsonNull()
                                    ? pNode.get("payment_method_id").getAsString()
                                    : "mercado_pago";
                            String dateApproved = pNode.has("date_approved") && !pNode.get("date_approved").isJsonNull()
                                    ? pNode.get("date_approved").getAsString()
                                    : null;

                            boolean isApproved = "approved".equalsIgnoreCase(status);
                            boolean isAmountCorrect = expectedAmount == null || (amountPaid.compareTo(expectedAmount) >= 0);

                            if (isApproved && isAmountCorrect) {
                                notificarBackendAprovado(pId, referenceId, amountPaid, method, dateApproved);
                                return new com.activeage.payment.model.PaymentVerificationResult(
                                        true,
                                        status,
                                        amountPaid,
                                        expectedAmount,
                                        pId,
                                        method,
                                        "Pagamento aprovado e verificado com sucesso no Mercado Pago."
                                );
                            } else if (!isApproved) {
                                return new com.activeage.payment.model.PaymentVerificationResult(
                                        false,
                                        status,
                                        amountPaid,
                                        expectedAmount,
                                        pId,
                                        method,
                                        "Pagamento localizado, porém ainda com status: " + status
                                );
                            } else {
                                return new com.activeage.payment.model.PaymentVerificationResult(
                                        false,
                                        status,
                                        amountPaid,
                                        expectedAmount,
                                        pId,
                                        method,
                                        "O valor pago (R$ " + amountPaid + ") é inferior ao valor exigido (R$ " + expectedAmount + ")."
                                );
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Erro na consulta de pagamentos por referência: " + e.getMessage());
            }
        }

        return new com.activeage.payment.model.PaymentVerificationResult(
                false,
                "not_found",
                BigDecimal.ZERO,
                expectedAmount,
                null,
                null,
                "Nenhum pagamento aprovado foi localizado no Mercado Pago para esta transação."
        );
    }

    private void notificarBackendAprovado(String paymentId, String referenceId, BigDecimal valorPago, String metodo, String dataPagamentoStr) {
        try {
            System.out.println("Notificando backend principal sobre pagamento aprovado: " + paymentId);
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

                double valDouble = valorPago != null ? valorPago.doubleValue() : 0.0;
                String dataStr = (dataPagamentoStr != null && !dataPagamentoStr.isBlank())
                        ? dataPagamentoStr
                        : LocalDateTime.now().toString();

                String jsonBody = String.format(
                        Locale.US,
                        "{\"transacaoId\":\"%s\",\"metodo\":\"%s\",\"valorPago\":%.2f,\"dataPagamento\":\"%s\"}",
                        paymentId != null ? paymentId : "MP-" + System.currentTimeMillis(),
                        metodo != null ? metodo : "mercado_pago",
                        valDouble,
                        dataStr
                );

                requestBuilder.uri(URI.create(mainBackendUrl + endpointTarget))
                        .header("Content-Type", "application/json")
                        .PUT(HttpRequest.BodyPublishers.ofString(jsonBody));
            }

            HttpRequest httpRequest = requestBuilder.build();
            HttpResponse<String> resp = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            System.out.println("Resposta do backend principal (" + endpointTarget + "): " + resp.statusCode());
        } catch (Exception e) {
            System.err.println("Erro ao notificar backend principal: " + e.getMessage());
        }
    }

    @Override
    public PaymentRefundResult refundPayment(String paymentId, BigDecimal amount, String reason) {
        System.out.println("Solicitando reembolso no Mercado Pago...");
        System.out.println("Parâmetros: PaymentId=" + paymentId + " | Valor=" + amount + " | Motivo=" + reason);

        if (paymentId == null || paymentId.isBlank()) {
            return new PaymentRefundResult(
                    false,
                    "failed",
                    null,
                    null,
                    amount != null ? amount : BigDecimal.ZERO,
                    LocalDateTime.now(),
                    "ID do pagamento não foi fornecido para estorno."
            );
        }

        String cleanPaymentId = paymentId.replaceAll("[^0-9]", "");

        // Se for um ID simulado de ambiente de desenvolvimento/teste (ex: "MP-123" ou string sem dígitos)
        if (cleanPaymentId.isBlank()) {
            String fakeRefundId = "REF-" + System.currentTimeMillis();
            System.out.println("Pagamento identificado como simulado/mock. Gerando estorno mock: " + fakeRefundId);
            return new PaymentRefundResult(
                    true,
                    "approved",
                    fakeRefundId,
                    paymentId,
                    amount != null ? amount : BigDecimal.ZERO,
                    LocalDateTime.now(),
                    "Estorno simulado processado com sucesso para ambiente de teste."
            );
        }

        try {
            PaymentRefundClient refundClient = new PaymentRefundClient();
            Long mpId = Long.parseLong(cleanPaymentId);
            PaymentRefund refund;

            if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
                refund = refundClient.refund(mpId, amount);
            } else {
                refund = refundClient.refund(mpId);
            }

            if (refund != null) {
                String refundIdStr = refund.getId() != null ? refund.getId().toString() : "REF-" + System.currentTimeMillis();
                String status = refund.getStatus() != null ? refund.getStatus() : "approved";
                BigDecimal refundedAmount = refund.getAmount() != null ? refund.getAmount() : (amount != null ? amount : BigDecimal.ZERO);

                return new PaymentRefundResult(
                        true,
                        status,
                        refundIdStr,
                        cleanPaymentId,
                        refundedAmount,
                        refund.getDateCreated() != null ? refund.getDateCreated().toLocalDateTime() : LocalDateTime.now(),
                        "Reembolso de R$ " + refundedAmount + " processado com sucesso no Mercado Pago."
                );
            }
        } catch (Exception e) {
            System.err.println("Erro ao estornar pagamento no Mercado Pago (" + paymentId + "): " + e.getMessage());
            return new PaymentRefundResult(
                    false,
                    "failed",
                    null,
                    cleanPaymentId,
                    amount != null ? amount : BigDecimal.ZERO,
                    LocalDateTime.now(),
                    "Falha ao processar estorno no Mercado Pago: " + e.getMessage()
            );
        }

        return new PaymentRefundResult(
                false,
                "failed",
                null,
                cleanPaymentId,
                amount != null ? amount : BigDecimal.ZERO,
                LocalDateTime.now(),
                "Não foi possível concluir o reembolso no Mercado Pago."
        );
    }
}