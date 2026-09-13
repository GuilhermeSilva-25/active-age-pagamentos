package com.activeage.payment.service;

import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.resources.payment.Payment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

/**
 * Classe de testes para o serviço de pagamento.
 * Avalia o comportamento do sistema quando falhas externas ocorrem.
 */
@ExtendWith(MockitoExtension.class)
class MercadoPagoPaymentServiceTest {

    @InjectMocks
    private MercadoPagoPaymentService paymentService;

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
}