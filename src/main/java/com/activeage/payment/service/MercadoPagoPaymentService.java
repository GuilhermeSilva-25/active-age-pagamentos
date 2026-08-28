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

@Service
public class MercadoPagoPaymentService implements PaymentService {

    @Value("${mercadopago.access-token}")
    private String accessToken;

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
                    .notificationUrl("https://portfolio-flop-exquisite.ngrok-free.dev/api/payments/webhook")
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
            System.out.println("🔔 Webhook Recebido!");
            System.out.println("ID do Pagamento: " + paymentId);
            System.out.println("Referência (Agendamento/Médico): " + referenceId);
            System.out.println("Status oficial: " + status);
        } catch (Exception e) {
            System.err.println("Erro ao processar webhook: " + e.getMessage());
        }
    }
}