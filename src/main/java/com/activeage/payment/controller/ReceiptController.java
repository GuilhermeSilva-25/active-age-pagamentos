package com.activeage.payment.controller;

import com.activeage.payment.model.PaymentReceipt;
import com.activeage.payment.service.PdfReceiptGenerator;
import com.activeage.payment.service.ReceiptService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controlador REST responsável pela geração de extratos e comprovantes de pagamento.
 * <p>
 * Expõe endpoints para consultar os dados reais de uma transação na API do
 * Mercado Pago e para gerar o comprovante em formato PDF.
 * <p>
 * Todos os dados são obtidos diretamente da API oficial do Mercado Pago.
 * Nenhum dado é simulado ou inventado.
 */
@RestController
@RequestMapping("/api/receipts")
@CrossOrigin(origins = "*")
public class ReceiptController {

    private final ReceiptService receiptService;
    private final PdfReceiptGenerator pdfGenerator;

    /**
     * Construtor para injeção de dependências.
     *
     * @param receiptService Serviço de extrato que consulta a API do Mercado Pago.
     * @param pdfGenerator   Gerador de documentos PDF.
     */
    public ReceiptController(ReceiptService receiptService, PdfReceiptGenerator pdfGenerator) {
        this.receiptService = receiptService;
        this.pdfGenerator = pdfGenerator;
    }

    /**
     * Retorna os dados do extrato/comprovante em formato JSON.
     * <p>
     * Consulta a API oficial do Mercado Pago pelo ID do pagamento
     * e retorna todos os dados reais disponíveis.
     *
     * @param paymentId   Identificador do pagamento no Mercado Pago.
     * @param referenceId Referência externa para validação de propriedade (opcional).
     * @return Dados do extrato com status de validação.
     */
    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentReceipt> getReceipt(
            @PathVariable String paymentId,
            @RequestParam(required = false) String referenceId
    ) {
        PaymentReceipt receipt;

        if (referenceId != null && !referenceId.isBlank()) {
            // Verifica se o pagamento pertence à referência informada
            receipt = receiptService.getReceiptWithOwnershipCheck(paymentId, referenceId);
        } else {
            receipt = receiptService.getReceipt(paymentId);
        }

        return ResponseEntity.ok(receipt);
    }

    /**
     * Gera e retorna o comprovante/extrato de pagamento em formato PDF.
     * <p>
     * Consulta a API oficial do Mercado Pago, valida os dados e gera
     * um documento PDF profissional para download.
     *
     * @param paymentId   Identificador do pagamento no Mercado Pago.
     * @param referenceId Referência externa para validação de propriedade (opcional).
     * @return PDF do comprovante para download.
     */
    @GetMapping("/{paymentId}/pdf")
    public ResponseEntity<byte[]> getReceiptPdf(
            @PathVariable String paymentId,
            @RequestParam(required = false) String referenceId
    ) {
        PaymentReceipt receipt;

        if (referenceId != null && !referenceId.isBlank()) {
            receipt = receiptService.getReceiptWithOwnershipCheck(paymentId, referenceId);
        } else {
            receipt = receiptService.getReceipt(paymentId);
        }

        if (!receipt.validated()) {
            return ResponseEntity.badRequest()
                    .header("X-Validation-Error", receipt.validationMessage())
                    .build();
        }

        try {
            byte[] pdf = pdfGenerator.gerar(receipt);

            // Nome do arquivo com o ID da transação
            String nomeArquivo = "comprovante-active-age-"
                    + (receipt.mercadoPagoId() != null ? receipt.mercadoPagoId() : "transacao")
                    + ".pdf";

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + nomeArquivo + "\"")
                    .body(pdf);

        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
