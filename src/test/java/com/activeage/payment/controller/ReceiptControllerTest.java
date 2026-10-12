package com.activeage.payment.controller;

import com.activeage.payment.model.PaymentReceipt;
import com.activeage.payment.service.PdfReceiptGenerator;
import com.activeage.payment.service.ReceiptService;
import com.lowagie.text.DocumentException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Testes unitários para o {@link ReceiptController}.
 * <p>
 * Verifica:
 * 1. Obtenção de extrato JSON (sucesso e validação)
 * 2. Obtenção com verificação de propriedade
 * 3. Geração e download do PDF com cabeçalhos apropriados
 * 4. Bloqueio de download do PDF para transações não validadas (HTTP 400)
 * 5. Tratamento de exceção interna na geração de PDF (HTTP 500)
 */
@ExtendWith(MockitoExtension.class)
class ReceiptControllerTest {

    @Mock
    private ReceiptService receiptService;

    @Mock
    private PdfReceiptGenerator pdfGenerator;

    @InjectMocks
    private ReceiptController receiptController;

    private PaymentReceipt criarReceiptValido(Long id, String status) {
        return new PaymentReceipt(
                id,
                "AGEND-101",
                "Consulta Fisioterapia",
                new BigDecimal("150.00"),
                new BigDecimal("142.50"),
                "BRL",
                status,
                "accredited",
                LocalDateTime.now(),
                LocalDateTime.now(),
                "pix",
                "bank_transfer",
                1,
                "paciente@email.com",
                "Ana",
                "Paula",
                "CPF",
                "***4321",
                true,
                "Pagamento aprovado e confirmado pelo Mercado Pago."
        );
    }

    private PaymentReceipt criarReceiptInvalido(Long id, String mensagem) {
        return new PaymentReceipt(
                id,
                null,
                null,
                null,
                null,
                null,
                "not_found",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                mensagem
        );
    }

    @Test
    void deveRetornarExtratoJsonComSucesso() {
        PaymentReceipt mockReceipt = criarReceiptValido(12345L, "approved");
        when(receiptService.getReceipt("12345")).thenReturn(mockReceipt);

        ResponseEntity<PaymentReceipt> response = receiptController.getReceipt("12345", null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().validated());
        assertEquals(12345L, response.getBody().mercadoPagoId());
        assertEquals("approved", response.getBody().status());
    }

    @Test
    void deveRetornarExtratoJsonComChecagemDePropriedade() {
        PaymentReceipt mockReceipt = criarReceiptValido(12345L, "approved");
        when(receiptService.getReceiptWithOwnershipCheck("12345", "AGEND-101")).thenReturn(mockReceipt);

        ResponseEntity<PaymentReceipt> response = receiptController.getReceipt("12345", "AGEND-101");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().validated());
        assertEquals("AGEND-101", response.getBody().externalReference());
        verify(receiptService, times(1)).getReceiptWithOwnershipCheck("12345", "AGEND-101");
        verify(receiptService, never()).getReceipt("12345");
    }

    @Test
    void deveRetornarExtratoJsonComStatusNaoValidadoQuandoInexistente() {
        PaymentReceipt mockInvalido = criarReceiptInvalido(99999L, "Pagamento não encontrado na API do Mercado Pago.");
        when(receiptService.getReceipt("99999")).thenReturn(mockInvalido);

        ResponseEntity<PaymentReceipt> response = receiptController.getReceipt("99999", null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().validated());
        assertEquals("not_found", response.getBody().status());
    }

    @Test
    void deveGerarEDescarregarPdfComSucessoParaPagamentoValido() throws Exception {
        PaymentReceipt mockReceipt = criarReceiptValido(12345L, "approved");
        byte[] pdfFake = "%PDF-1.4 teste".getBytes();

        when(receiptService.getReceipt("12345")).thenReturn(mockReceipt);
        when(pdfGenerator.gerar(mockReceipt)).thenReturn(pdfFake);

        ResponseEntity<byte[]> response = receiptController.getReceiptPdf("12345", null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(MediaType.APPLICATION_PDF, response.getHeaders().getContentType());
        assertTrue(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)
                .contains("comprovante-active-age-12345.pdf"));
        assertArrayEquals(pdfFake, response.getBody());
    }

    @Test
    void deveRetornarBadRequestAoTentarGerarPdfDePagamentoNaoValidado() {
        PaymentReceipt mockInvalido = criarReceiptInvalido(99999L, "Pagamento não encontrado na API.");
        when(receiptService.getReceipt("99999")).thenReturn(mockInvalido);

        ResponseEntity<byte[]> response = receiptController.getReceiptPdf("99999", null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNull(response.getBody());
        assertEquals("Pagamento não encontrado na API.", response.getHeaders().getFirst("X-Validation-Error"));
        verifyNoInteractions(pdfGenerator);
    }

    @Test
    void deveBloquearPdfQuandoPropriedadeNaoCorresponder() {
        PaymentReceipt mockInvalido = criarReceiptInvalido(12345L, "O pagamento informado não pertence à referência solicitada (AGEND-999).");
        when(receiptService.getReceiptWithOwnershipCheck("12345", "AGEND-999")).thenReturn(mockInvalido);

        ResponseEntity<byte[]> response = receiptController.getReceiptPdf("12345", "AGEND-999");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNull(response.getBody());
        verifyNoInteractions(pdfGenerator);
    }

    @Test
    void deveRetornarInternalServerErrorQuandoFalhaNaGeracaoDoPdf() throws Exception {
        PaymentReceipt mockReceipt = criarReceiptValido(12345L, "approved");
        when(receiptService.getReceipt("12345")).thenReturn(mockReceipt);
        when(pdfGenerator.gerar(mockReceipt)).thenThrow(new DocumentException("Erro ao compor PDF"));

        ResponseEntity<byte[]> response = receiptController.getReceiptPdf("12345", null);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }
}
