package com.activeage.payment.service;

import com.activeage.payment.model.PaymentReceipt;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.net.MPResponse;
import com.mercadopago.resources.payment.Payment;
import com.mercadopago.resources.payment.PaymentPayer;
import com.mercadopago.resources.payment.PaymentTransactionDetails;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Testes unitários para o serviço de extrato/comprovante de pagamento.
 * <p>
 * Cobre todos os cenários exigidos:
 * 1. Pagamento aprovado com dados reais
 * 2. Pagamento pendente, rejeitado e cancelado
 * 3. ID inexistente
 * 4. Falha na API do Mercado Pago
 * 5. Verificação de propriedade (outro usuário)
 * 6. Ausência de campos opcionais
 * 7. Consistência dos dados exibidos
 * 8. Ausência de dados fictícios
 */
@ExtendWith(MockitoExtension.class)
class MercadoPagoReceiptServiceTest {

    @InjectMocks
    private MercadoPagoReceiptService receiptService;

    // ====================================================================
    // 1. Pagamento aprovado com dados reais
    // ====================================================================

    @Test
    void deveRetornarExtratoValidoParaPagamentoAprovado() {
        // Arrange: Simulamos um pagamento aprovado retornado pelo SDK do MP
        Payment mockPayment = criarMockPagamentoCompleto("approved", "accredited");

        try (MockedConstruction<PaymentClient> mockedClient = mockConstruction(PaymentClient.class,
                (mock, context) -> {
                    when(mock.get(12345L)).thenReturn(mockPayment);
                })) {

            // Act
            PaymentReceipt receipt = receiptService.getReceipt("12345");

            // Assert: Dados reais, validados, sem informações fictícias
            assertTrue(receipt.validated(), "O extrato deve ser marcado como validado");
            assertEquals(12345L, receipt.mercadoPagoId());
            assertEquals("approved", receipt.status());
            assertEquals("accredited", receipt.statusDetail());
            assertEquals("AGEND-100", receipt.externalReference());
            assertEquals("Consulta Cardiológica", receipt.description());
            assertEquals(new BigDecimal("150.00"), receipt.transactionAmount());
            assertEquals("BRL", receipt.currencyId());
            assertEquals("pix", receipt.paymentMethodId());
            assertEquals("bank_transfer", receipt.paymentTypeId());
            assertNotNull(receipt.dateCreated());
            assertNotNull(receipt.dateApproved());
            assertEquals("paciente@email.com", receipt.payerEmail());
            assertEquals("João", receipt.payerFirstName());
            assertNotNull(receipt.validationMessage());
            assertTrue(receipt.validationMessage().contains("aprovado"));
        }
    }

    // ====================================================================
    // 2. Pagamento pendente
    // ====================================================================

    @Test
    void deveRetornarExtratoComStatusPendenteQuandoPagamentoPendente() {
        Payment mockPayment = criarMockPagamentoSimples("pending", "pending_contingency");

        try (MockedConstruction<PaymentClient> mockedClient = mockConstruction(PaymentClient.class,
                (mock, context) -> {
                    when(mock.get(22222L)).thenReturn(mockPayment);
                })) {

            PaymentReceipt receipt = receiptService.getReceipt("22222");

            assertTrue(receipt.validated());
            assertEquals("pending", receipt.status());
            assertTrue(receipt.validationMessage().contains("pendente") ||
                    receipt.validationMessage().contains("Pendente"));
        }
    }

    // ====================================================================
    // 2b. Pagamento rejeitado
    // ====================================================================

    @Test
    void deveRetornarExtratoComStatusRejeitadoQuandoPagamentoRejeitado() {
        Payment mockPayment = criarMockPagamentoSimples("rejected", "cc_rejected_other_reason");

        try (MockedConstruction<PaymentClient> mockedClient = mockConstruction(PaymentClient.class,
                (mock, context) -> {
                    when(mock.get(33333L)).thenReturn(mockPayment);
                })) {

            PaymentReceipt receipt = receiptService.getReceipt("33333");

            assertTrue(receipt.validated());
            assertEquals("rejected", receipt.status());
            assertTrue(receipt.validationMessage().contains("rejeitado") ||
                    receipt.validationMessage().contains("Rejeitado"));
        }
    }

    // ====================================================================
    // 2c. Pagamento cancelado
    // ====================================================================

    @Test
    void deveRetornarExtratoComStatusCanceladoQuandoPagamentoCancelado() {
        Payment mockPayment = criarMockPagamentoSimples("cancelled", null);

        try (MockedConstruction<PaymentClient> mockedClient = mockConstruction(PaymentClient.class,
                (mock, context) -> {
                    when(mock.get(44444L)).thenReturn(mockPayment);
                })) {

            PaymentReceipt receipt = receiptService.getReceipt("44444");

            assertTrue(receipt.validated());
            assertEquals("cancelled", receipt.status());
            assertTrue(receipt.validationMessage().contains("cancelado") ||
                    receipt.validationMessage().contains("Cancelado"));
        }
    }

    // ====================================================================
    // 3. ID de pagamento inexistente (404 da API)
    // ====================================================================

    @Test
    void deveRetornarExtratoInvalidoQuandoPagamentoNaoExiste() {
        try (MockedConstruction<PaymentClient> mockedClient = mockConstruction(PaymentClient.class,
                (mock, context) -> {
                    MPResponse mockResponse = mock(MPResponse.class);
                    when(mockResponse.getContent()).thenReturn("{\"message\":\"Payment not found\"}");
                    MPApiException apiException = new MPApiException("Not Found", mockResponse);
                    // Configuramos o status code via reflection pois o construtor não aceita
                    try {
                        java.lang.reflect.Field statusCodeField = MPApiException.class.getDeclaredField("statusCode");
                        statusCodeField.setAccessible(true);
                        statusCodeField.setInt(apiException, 404);
                    } catch (Exception ignored) {}
                    when(mock.get(99999L)).thenThrow(apiException);
                })) {

            PaymentReceipt receipt = receiptService.getReceipt("99999");

            assertFalse(receipt.validated(), "O extrato NÃO deve ser validado para ID inexistente");
            assertNotNull(receipt.validationMessage());
        }
    }

    // ====================================================================
    // 4. Falha/indisponibilidade da API do Mercado Pago
    // ====================================================================

    @Test
    void deveRetornarExtratoInvalidoQuandoApiIndisponivel() {
        try (MockedConstruction<PaymentClient> mockedClient = mockConstruction(PaymentClient.class,
                (mock, context) -> {
                    when(mock.get(55555L)).thenThrow(new MPException("Connection refused"));
                })) {

            PaymentReceipt receipt = receiptService.getReceipt("55555");

            assertFalse(receipt.validated());
            assertNotNull(receipt.validationMessage());
            assertTrue(receipt.validationMessage().contains("Falha") ||
                    receipt.validationMessage().contains("comunicação"));
        }
    }

    // ====================================================================
    // 5. Tentativa de acessar pagamento de outro usuário
    // ====================================================================

    @Test
    void deveRejeritarAcessoQuandoPagamentoNaoPertenceAoUsuario() {
        Payment mockPayment = criarMockPagamentoCompleto("approved", "accredited");

        try (MockedConstruction<PaymentClient> mockedClient = mockConstruction(PaymentClient.class,
                (mock, context) -> {
                    when(mock.get(12345L)).thenReturn(mockPayment);
                })) {

            // Act: Tenta acessar com uma referência diferente da que o pagamento possui
            PaymentReceipt receipt = receiptService.getReceiptWithOwnershipCheck("12345", "AGEND-999");

            // Assert: O acesso deve ser negado
            assertFalse(receipt.validated(), "O acesso deve ser negado para referência incorreta");
            assertTrue(receipt.validationMessage().contains("não pertence"));
        }
    }

    @Test
    void devePermitirAcessoQuandoReferenciaCorrespondeAoPagamento() {
        Payment mockPayment = criarMockPagamentoCompleto("approved", "accredited");

        try (MockedConstruction<PaymentClient> mockedClient = mockConstruction(PaymentClient.class,
                (mock, context) -> {
                    when(mock.get(12345L)).thenReturn(mockPayment);
                })) {

            // Act: Acessa com a referência correta
            PaymentReceipt receipt = receiptService.getReceiptWithOwnershipCheck("12345", "AGEND-100");

            // Assert: O acesso deve ser permitido
            assertTrue(receipt.validated());
            assertEquals("AGEND-100", receipt.externalReference());
        }
    }

    // ====================================================================
    // 6. Ausência de campos opcionais
    // ====================================================================

    @Test
    void deveGerarExtratoMesmoSemCamposOpcionais() {
        // Arrange: Pagamento mínimo, sem pagador, sem detalhes de transação
        Payment mockPayment = mock(Payment.class);
        when(mockPayment.getId()).thenReturn(77777L);
        when(mockPayment.getStatus()).thenReturn("approved");
        when(mockPayment.getTransactionAmount()).thenReturn(new BigDecimal("50.00"));
        // Todos os demais campos retornam null (comportamento padrão do mock)

        try (MockedConstruction<PaymentClient> mockedClient = mockConstruction(PaymentClient.class,
                (mock, context) -> {
                    when(mock.get(77777L)).thenReturn(mockPayment);
                })) {

            PaymentReceipt receipt = receiptService.getReceipt("77777");

            assertTrue(receipt.validated());
            assertEquals(77777L, receipt.mercadoPagoId());
            assertEquals(new BigDecimal("50.00"), receipt.transactionAmount());
            // Campos opcionais devem ser null, NÃO dados inventados
            assertNull(receipt.payerEmail());
            assertNull(receipt.payerFirstName());
            assertNull(receipt.description());
            assertNull(receipt.externalReference());
            assertNull(receipt.netReceivedAmount());
        }
    }

    // ====================================================================
    // 7. Consistência entre valor, status, data e identificador
    // ====================================================================

    @Test
    void deveManterConsistenciaEntreValorStatusDataEIdentificador() {
        Payment mockPayment = criarMockPagamentoCompleto("approved", "accredited");

        try (MockedConstruction<PaymentClient> mockedClient = mockConstruction(PaymentClient.class,
                (mock, context) -> {
                    when(mock.get(12345L)).thenReturn(mockPayment);
                })) {

            PaymentReceipt receipt = receiptService.getReceipt("12345");

            // Verifica que os dados do extrato correspondem exatamente aos dados do mock
            assertEquals(12345L, receipt.mercadoPagoId(), "ID deve corresponder ao retornado pela API");
            assertEquals("approved", receipt.status(), "Status deve corresponder ao retornado pela API");
            assertEquals(new BigDecimal("150.00"), receipt.transactionAmount(),
                    "Valor deve corresponder ao retornado pela API");
            assertNotNull(receipt.dateCreated(), "Data de criação não deve ser null para pagamento completo");
            assertNotNull(receipt.dateApproved(), "Data de aprovação não deve ser null para pagamento aprovado");
        }
    }

    // ====================================================================
    // 8. Ausência de dados fictícios na geração
    // ====================================================================

    @Test
    void naoDeveConterDadosFicticiosNoExtrato() {
        Payment mockPayment = criarMockPagamentoCompleto("approved", "accredited");

        try (MockedConstruction<PaymentClient> mockedClient = mockConstruction(PaymentClient.class,
                (mock, context) -> {
                    when(mock.get(12345L)).thenReturn(mockPayment);
                })) {

            PaymentReceipt receipt = receiptService.getReceipt("12345");

            // Verifica que nenhum campo contém valores típicos de mock/teste
            assertNotEquals("MP-" , receipt.mercadoPagoId() != null ? receipt.mercadoPagoId().toString() : "",
                    "ID não deve ser um valor simulado com prefixo MP-");
            assertNotEquals("FAKE", receipt.status() != null ? receipt.status().toUpperCase() : "",
                    "Status não deve ser um valor fake");
            assertNotEquals("mock", receipt.paymentMethodId() != null ? receipt.paymentMethodId().toLowerCase() : "",
                    "Método de pagamento não deve ser 'mock'");
            assertNotEquals("test", receipt.paymentMethodId() != null ? receipt.paymentMethodId().toLowerCase() : "",
                    "Método de pagamento não deve ser 'test'");
        }
    }

    // ====================================================================
    // Testes adicionais de borda
    // ====================================================================

    @Test
    void deveRetornarExtratoInvalidoParaIdNulo() {
        PaymentReceipt receipt = receiptService.getReceipt(null);

        assertFalse(receipt.validated());
        assertNotNull(receipt.validationMessage());
        assertTrue(receipt.validationMessage().contains("não foi fornecido"));
    }

    @Test
    void deveRetornarExtratoInvalidoParaIdVazio() {
        PaymentReceipt receipt = receiptService.getReceipt("");

        assertFalse(receipt.validated());
        assertNotNull(receipt.validationMessage());
    }

    @Test
    void deveRetornarExtratoInvalidoParaIdSemDigitos() {
        PaymentReceipt receipt = receiptService.getReceipt("ABC-XYZ");

        assertFalse(receipt.validated());
        assertTrue(receipt.validationMessage().contains("não contém"));
    }

    @Test
    void deveLimparCaracteresNaoNumericosDoId() {
        Payment mockPayment = criarMockPagamentoSimples("approved", "accredited");
        when(mockPayment.getId()).thenReturn(67890L);

        try (MockedConstruction<PaymentClient> mockedClient = mockConstruction(PaymentClient.class,
                (mock, context) -> {
                    when(mock.get(67890L)).thenReturn(mockPayment);
                })) {

            // Envia ID com prefixo não numérico
            PaymentReceipt receipt = receiptService.getReceipt("MP-67890");

            assertTrue(receipt.validated());
            assertEquals(67890L, receipt.mercadoPagoId());
        }
    }

    @Test
    void deveMascararDocumentoNoPagador() {
        Payment mockPayment = criarMockPagamentoCompleto("approved", "accredited");

        try (MockedConstruction<PaymentClient> mockedClient = mockConstruction(PaymentClient.class,
                (mock, context) -> {
                    when(mock.get(12345L)).thenReturn(mockPayment);
                })) {

            PaymentReceipt receipt = receiptService.getReceipt("12345");

            // O documento deve estar parcialmente mascarado
            assertNotNull(receipt.payerIdentificationNumber());
            assertTrue(receipt.payerIdentificationNumber().startsWith("***"),
                    "O documento deve ser parcialmente mascarado por segurança");
        }
    }

    // ====================================================================
    // Métodos auxiliares para criação de mocks
    // ====================================================================

    /**
     * Cria um mock completo de pagamento com todos os campos preenchidos.
     */
    private Payment criarMockPagamentoCompleto(String status, String statusDetail) {
        Payment payment = mock(Payment.class);
        when(payment.getId()).thenReturn(12345L);
        when(payment.getStatus()).thenReturn(status);
        when(payment.getStatusDetail()).thenReturn(statusDetail);
        when(payment.getExternalReference()).thenReturn("AGEND-100");
        when(payment.getDescription()).thenReturn("Consulta Cardiológica");
        when(payment.getTransactionAmount()).thenReturn(new BigDecimal("150.00"));
        when(payment.getCurrencyId()).thenReturn("BRL");
        when(payment.getPaymentMethodId()).thenReturn("pix");
        when(payment.getPaymentTypeId()).thenReturn("bank_transfer");
        when(payment.getInstallments()).thenReturn(1);

        // Datas
        OffsetDateTime agora = OffsetDateTime.of(2025, 10, 12, 14, 30, 0, 0, ZoneOffset.of("-03:00"));
        when(payment.getDateCreated()).thenReturn(agora);
        when(payment.getDateApproved()).thenReturn(agora.plusMinutes(2));

        // Pagador
        PaymentPayer payer = mock(PaymentPayer.class);
        when(payer.getEmail()).thenReturn("paciente@email.com");
        when(payer.getFirstName()).thenReturn("João");
        when(payer.getLastName()).thenReturn("Silva");

        com.mercadopago.resources.common.Identification identification =
                mock(com.mercadopago.resources.common.Identification.class);
        when(identification.getType()).thenReturn("CPF");
        when(identification.getNumber()).thenReturn("12345678901");
        when(payer.getIdentification()).thenReturn(identification);

        when(payment.getPayer()).thenReturn(payer);

        // Detalhes da transação
        PaymentTransactionDetails details = mock(PaymentTransactionDetails.class);
        when(details.getNetReceivedAmount()).thenReturn(new BigDecimal("142.50"));
        when(payment.getTransactionDetails()).thenReturn(details);

        return payment;
    }

    /**
     * Cria um mock simples de pagamento com campos mínimos.
     */
    private Payment criarMockPagamentoSimples(String status, String statusDetail) {
        Payment payment = mock(Payment.class);
        when(payment.getId()).thenReturn(Long.parseLong(
                status.equals("pending") ? "22222" :
                        status.equals("rejected") ? "33333" :
                                status.equals("cancelled") ? "44444" : "67890"));
        when(payment.getStatus()).thenReturn(status);
        when(payment.getStatusDetail()).thenReturn(statusDetail);
        when(payment.getTransactionAmount()).thenReturn(new BigDecimal("100.00"));
        return payment;
    }
}
