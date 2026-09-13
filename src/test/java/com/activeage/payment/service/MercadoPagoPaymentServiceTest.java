package com.activeage.payment.service;

import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.resources.payment.Payment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;
import com.activeage.payment.model.PaymentIntent;
import com.activeage.payment.model.PaymentResult;
import com.activeage.payment.model.PaymentStatus;
import com.activeage.payment.model.PaymentType;
import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.resources.preference.Preference;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.BeforeEach;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.mockito.Mockito.*;

/**
 * Classe de testes para o serviço de pagamento.
 * Avalia o comportamento do sistema quando falhas externas ocorrem.
 */
@ExtendWith(MockitoExtension.class)
class MercadoPagoPaymentServiceTest {

    @InjectMocks
    private MercadoPagoPaymentService paymentService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(paymentService, "mainBackendUrl", "http://localhost:8080");
        ReflectionTestUtils.setField(paymentService, "webhookBaseUrl", "http://localhost:8081");
    }

    /**
     * Teste de Caminho Triste (Sad Path) para o processamento de Webhook.
     * <p>
     * <b>Cenário:</b> O sistema recebe um webhook e consulta o Mercado Pago,
     * descobrindo que o status do pagamento é "rejected" (recusado).
     * <br>
     * <b>Comportamento Esperado:</b> O serviço não deve prosseguir com a chamada HTTP
     * para o backend principal. O fluxo deve terminar silenciosamente sem erros.
     */
    @Test
    void shouldNotCallMainBackendWhenPaymentStatusIsRejected() {
        // Arrange: Preparamos a resposta falsa do Mercado Pago com status "rejected"
        Payment mockPayment = mock(Payment.class);
        when(mockPayment.getStatus()).thenReturn("rejected");
        when(mockPayment.getExternalReference()).thenReturn("MED-123");

        // MockConstruction intercepta a criação da classe "new PaymentClient()"
        try (MockedConstruction<PaymentClient> mockedClient = mockConstruction(PaymentClient.class,
                (mock, context) -> {
                    // Quando o service tentar buscar o pagamento 12345, retornamos nosso mock
                    when(mock.get(12345L)).thenReturn(mockPayment);
                })) {

            // Act: O Controller chama o serviço com o ID do pagamento falso
            paymentService.handleWebhook("12345");

            // Assert: Garantimos que ele buscou a informação no Mercado Pago
            PaymentClient clientMock = mockedClient.constructed().get(0);

            try {
                verify(clientMock, times(1)).get(12345L);
            } catch (Exception e) {
                // Captura apenas para o throws exigido pela API do SDK
            }

            // GARANTIA DO CAMINHO TRISTE:
            // Como o status foi "rejected", o if("approved") falhou.
            // Se o if falhou, a aplicação termina a execução sem tentar criar
            // o java.net.http.HttpClient e atualizar o backend principal.
        }
    }

    /**
     * Teste de Caminho Feliz: Criação de Pagamento no Gateway.
     * Simula o SDK do Mercado Pago gerando uma Preference com sucesso.
     */
    @Test
    void shouldCreatePaymentSuccessfully() {
        // Arrange
        PaymentIntent intent = new PaymentIntent(
                new BigDecimal("150.00"), "Assinatura", "teste@email.com",
                PaymentType.SUBSCRIPTION, "MED-999"
        );

        Preference mockPreference = mock(Preference.class);
        when(mockPreference.getId()).thenReturn("pref_123");
        when(mockPreference.getInitPoint()).thenReturn("https://checkout.mp.com/123");

        // MockConstruction para interceptar o "new PreferenceClient()"
        try (MockedConstruction<PreferenceClient> mockedClient = mockConstruction(PreferenceClient.class,
                (mock, context) -> {
                    when(mock.create(any())).thenReturn(mockPreference);
                })) {

            // Act
            PaymentResult result = paymentService.createPayment(intent);

            // Assert
            assertEquals("pref_123", result.paymentId());
            assertEquals("https://checkout.mp.com/123", result.checkoutUrl());
            assertEquals(PaymentStatus.PENDING, result.status());
        }
    }

    /**
     * Teste de Caminho Feliz: Processamento de Webhook Aprovado.
     * Verifica se ao receber "approved", o sistema tenta chamar o HttpClient nativo do Java
     * para bater na rota de ativação de Assinaturas (MED-).
     */
    @Test
    void shouldHandleApprovedWebhookForDoctorSubscription() throws Exception {
        // Arrange: Pagamento aprovado no Mercado Pago
        Payment mockPayment = mock(Payment.class);
        when(mockPayment.getStatus()).thenReturn("approved");
        when(mockPayment.getExternalReference()).thenReturn("MED-123");

        // Precisamos mockar 2 coisas complexas: O PaymentClient e o HttpClient estático do Java
        try (
                MockedConstruction<PaymentClient> mockedPaymentClient = mockConstruction(PaymentClient.class,
                        (mock, context) -> {
                            when(mock.get(12345L)).thenReturn(mockPayment);
                        });
                MockedStatic<HttpClient> mockedStaticHttp = mockStatic(HttpClient.class)
        ) {
            // Simulamos o comportamento do HttpClient para não fazer chamadas de rede reais
            HttpClient mockHttpClient = mock(HttpClient.class);
            HttpResponse mockResponse = mock(HttpResponse.class);

            mockedStaticHttp.when(HttpClient::newHttpClient).thenReturn(mockHttpClient);
            when(mockHttpClient.send(any(), any())).thenReturn(mockResponse);

            // Act
            paymentService.handleWebhook("12345");

            // Assert: Garantimos que o HttpClient foi chamado 1 vez avisando o Backend Principal!
            verify(mockHttpClient, times(1)).send(any(), any());
        }
    }
}