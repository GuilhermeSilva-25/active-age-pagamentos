package com.activeage.payment.controller;

import com.activeage.payment.model.PaymentIntent;
import com.activeage.payment.model.PaymentResult;
import com.activeage.payment.model.WebhookNotification;
import com.activeage.payment.service.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controlador REST responsável por gerenciar as requisições relacionadas a pagamentos.
 *
 * Expõe os endpoints para a criação de novas intenções de pagamento e também
 * para o recebimento de notificações assíncronas (webhooks) dos gateways de pagamento.
 */
@RestController
@RequestMapping("/api/payments")
@CrossOrigin(origins = "*")
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * Construtor para injeção de dependência do serviço de pagamentos.
     *
     * @param paymentService O serviço de pagamentos a ser utilizado pelo controlador.
     */
    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * Cria uma nova transação de pagamento.
     *
     * Este endpoint recebe os dados da intenção de pagamento, como valor e referência,
     * e delega a criação para o serviço de pagamentos, retornando o link do checkout gerado.
     *
     * @param intent Objeto contendo os detalhes do pagamento desejado.
     * @return Uma resposta HTTP contendo o resultado da criação e a URL de pagamento.
     */
    @PostMapping("/create")
    public ResponseEntity<PaymentResult> createPayment(@RequestBody PaymentIntent intent) {
        PaymentResult result = paymentService.createPayment(intent);
        return ResponseEntity.ok(result);
    }

    /**
     * Recebe e processa notificações de webhook enviadas pelo gateway de pagamento.
     *
     * Este endpoint atua como um ouvinte para os eventos do Mercado Pago. Ele filtra
     * notificações do tipo "payment" e repassa o ID da transação para o serviço validar.
     *
     * @param notification Objeto contendo o tipo de evento e os dados enviados pelo gateway.
     * @return Uma resposta HTTP simples confirmando o recebimento do aviso.
     */
    @PostMapping("/webhook")
    public ResponseEntity<String> receiveWebhook(@RequestBody WebhookNotification notification) {

        if ("payment".equals(notification.type()) && notification.data() != null) {
            String paymentId = notification.data().get("id");

            paymentService.handleWebhook(paymentId);
        }

        return ResponseEntity.ok("Aviso Recebido");
    }

    /**
     * Consulta a API do Mercado Pago para verificar de forma segura e oficial se o pagamento
     * foi concluído com status "approved" e se o valor pago é o correto.
     *
     * @param referenceId Referência externa (ex: AGEND-123 ou MED-456).
     * @param paymentId ID da transação no Mercado Pago (opcional).
     * @param expectedAmount Valor esperado para a teleconsulta ou plano.
     * @return Resultado da validação de segurança.
     */
    @GetMapping("/verify")
    public ResponseEntity<com.activeage.payment.model.PaymentVerificationResult> verifyPayment(
            @RequestParam(required = false) String referenceId,
            @RequestParam(required = false) String paymentId,
            @RequestParam(required = false) java.math.BigDecimal expectedAmount
    ) {
        com.activeage.payment.model.PaymentVerificationResult result = paymentService.verifyPayment(referenceId, paymentId, expectedAmount);
        return ResponseEntity.ok(result);
    }

    /**
     * Solicita o estorno/reembolso de uma transação no Mercado Pago via payload JSON.
     *
     * @param request Objeto contendo o ID do pagamento, valor opcional e justificativa.
     * @return O resultado oficial do processamento de reembolso.
     */
    @PostMapping("/refund")
    public ResponseEntity<com.activeage.payment.model.PaymentRefundResult> refundPayment(
            @RequestBody com.activeage.payment.model.PaymentRefundRequest request
    ) {
        com.activeage.payment.model.PaymentRefundResult result = paymentService.refundPayment(
                request.paymentId(),
                request.amount(),
                request.reason()
        );
        return ResponseEntity.ok(result);
    }

    /**
     * Solicita o estorno/reembolso de uma transação no Mercado Pago via PathVariable.
     *
     * @param paymentId ID da transação no Mercado Pago.
     * @param amount    Valor opcional para estorno parcial.
     * @param reason    Motivo opcional do estorno.
     * @return O resultado oficial do processamento de reembolso.
     */
    @PostMapping("/{paymentId}/refund")
    public ResponseEntity<com.activeage.payment.model.PaymentRefundResult> refundPaymentById(
            @PathVariable String paymentId,
            @RequestParam(required = false) java.math.BigDecimal amount,
            @RequestParam(required = false) String reason
    ) {
        com.activeage.payment.model.PaymentRefundResult result = paymentService.refundPayment(
                paymentId,
                amount,
                reason
        );
        return ResponseEntity.ok(result);
    }
}