package com.activeage.payment.controller;

import com.activeage.payment.model.WebhookNotification;
import com.activeage.payment.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Classe de testes unitários para o {@link PaymentController}.
 *
 * Foca em garantir que os endpoints de pagamento respondam corretamente
 * tanto aos fluxos ideais quanto aos fluxos de erro ou dados inesperados (Caminhos Tristes).
 */
@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private PaymentController paymentController;

    /**
     * Teste de Caminho Triste (Sad Path) para o endpoint de Webhook.
     * <p>
     * <b>Cenário:</b> O gateway de pagamento envia um evento de um tipo não relacionado
     * a pagamentos (ex: "subscription", "plan").
     * <br>
     * <b>Comportamento Esperado:</b> O Controller deve processar a requisição e retornar {@code 200 OK}
     * para confirmar o recebimento ao gateway, mas <b>não deve</b> repassar a ação
     * para o {@link PaymentService}.
     */
    @Test
    void shouldIgnoreWebhookWhenTypeIsNotPayment() {
        // Arrange: Criamos um payload (Caminho Triste) onde o tipo é diferente de "payment"
        WebhookNotification notification = new WebhookNotification(
                "subscription",
                Map.of("id", "12345")
        );

        // Act: Simulamos a chamada ao Controller
        ResponseEntity<String> response = paymentController.receiveWebhook(notification);

        // Assert: Validamos o comportamento esperado

        // 1. O retorno deve ser 200 OK para evitar repetições do gateway
        assertEquals(HttpStatus.OK, response.getStatusCode(), "O HTTP Status deve ser 200 OK");
        assertEquals("Aviso Recebido", response.getBody(), "O corpo da resposta deve ser 'Aviso Recebido'");

        // 2. GARANTIA DO CAMINHO TRISTE: O Service não deve ser acionado!
        verifyNoInteractions(paymentService);
    }
}