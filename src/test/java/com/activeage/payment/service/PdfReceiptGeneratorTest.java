package com.activeage.payment.service;

import com.activeage.payment.model.PaymentReceipt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes unitários para o gerador de comprovantes e extratos em formato PDF.
 * <p>
 * Valida a geração de PDFs válidos para:
 * - Pagamento aprovado ("COMPROVANTE DE PAGAMENTO")
 * - Pagamento pendente ("EXTRATO DE TRANSAÇÃO")
 * - Pagamento rejeitado e cancelado
 * - Pagamento sem dados opcionais do pagador
 * - Pagamento com parcelamento
 * - Pagamento não validado (com aviso de erro)
 */
class PdfReceiptGeneratorTest {

    private PdfReceiptGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new PdfReceiptGenerator();
    }

    @Test
    void deveGerarPdfValidoParaPagamentoAprovado() throws Exception {
        PaymentReceipt receipt = new PaymentReceipt(
                123456789L,
                "AGEND-100",
                "Consulta Médica Geriatria",
                new BigDecimal("250.00"),
                new BigDecimal("240.00"),
                "BRL",
                "approved",
                "accredited",
                LocalDateTime.of(2026, 10, 12, 10, 0, 0),
                LocalDateTime.of(2026, 10, 12, 10, 2, 0),
                "pix",
                "bank_transfer",
                1,
                "paciente@email.com",
                "Maria",
                "Silva",
                "CPF",
                "***1234",
                true,
                "Pagamento aprovado e confirmado pelo Mercado Pago."
        );

        byte[] pdfBytes = generator.gerar(receipt);

        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0, "O PDF gerado não deve estar vazio");

        // Valida assinatura padrão de cabeçalho de arquivo PDF (%PDF-)
        String cabecalho = new String(pdfBytes, 0, Math.min(pdfBytes.length, 5));
        assertEquals("%PDF-", cabecalho, "O arquivo gerado deve iniciar com o cabeçalho PDF padrão");
    }

    @Test
    void deveGerarPdfParaPagamentoPendente() throws Exception {
        PaymentReceipt receipt = new PaymentReceipt(
                222333444L,
                "AGEND-200",
                "Teleconsulta Fisioterapia",
                new BigDecimal("120.00"),
                null,
                "BRL",
                "pending",
                "pending_review_manual",
                LocalDateTime.of(2026, 10, 12, 11, 0, 0),
                null,
                "bolbradesco",
                "ticket",
                null,
                "paciente2@email.com",
                "José",
                "Santos",
                "CPF",
                "***5678",
                true,
                "Pagamento pendente de confirmação. Aguardando processamento."
        );

        byte[] pdfBytes = generator.gerar(receipt);

        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0);
        String cabecalho = new String(pdfBytes, 0, 5);
        assertEquals("%PDF-", cabecalho);
    }

    @Test
    void deveGerarPdfParaPagamentoRejeitado() throws Exception {
        PaymentReceipt receipt = new PaymentReceipt(
                333444555L,
                "AGEND-300",
                "Consulta Nutricional",
                new BigDecimal("180.00"),
                null,
                "BRL",
                "rejected",
                "cc_rejected_insufficient_amount",
                LocalDateTime.of(2026, 10, 12, 12, 0, 0),
                null,
                "visa",
                "credit_card",
                1,
                null,
                null,
                null,
                null,
                null,
                true,
                "Pagamento rejeitado pelo Mercado Pago. O valor não foi cobrado."
        );

        byte[] pdfBytes = generator.gerar(receipt);

        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0);
    }

    @Test
    void deveGerarPdfComParcelamento() throws Exception {
        PaymentReceipt receipt = new PaymentReceipt(
                444555666L,
                "MED-999",
                "Assinatura Anual Médico",
                new BigDecimal("1200.00"),
                new BigDecimal("1140.00"),
                "BRL",
                "approved",
                "accredited",
                LocalDateTime.of(2026, 10, 12, 9, 30, 0),
                LocalDateTime.of(2026, 10, 12, 9, 31, 0),
                "master",
                "credit_card",
                12,
                "medico@clinica.com",
                "Carlos",
                "Oliveira",
                "CRM",
                "***7890",
                true,
                "Pagamento aprovado e confirmado pelo Mercado Pago."
        );

        byte[] pdfBytes = generator.gerar(receipt);

        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0);
    }

    @Test
    void deveGerarPdfMesmoSemCamposOpcionais() throws Exception {
        PaymentReceipt receiptMinimo = new PaymentReceipt(
                555666777L,
                null,
                null,
                new BigDecimal("90.00"),
                null,
                null,
                "approved",
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
                true,
                "Pagamento aprovado e confirmado pelo Mercado Pago."
        );

        byte[] pdfBytes = generator.gerar(receiptMinimo);

        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0);
    }

    @Test
    void deveGerarPdfComAvisoQuandoNaoValidado() throws Exception {
        PaymentReceipt receiptInvalido = new PaymentReceipt(
                999999L,
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
                "Pagamento não encontrado na API do Mercado Pago."
        );

        byte[] pdfBytes = generator.gerar(receiptInvalido);

        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0);
    }
}
