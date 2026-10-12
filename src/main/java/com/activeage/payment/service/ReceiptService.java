package com.activeage.payment.service;

import com.activeage.payment.model.PaymentReceipt;

/**
 * Contrato para o serviço de extrato/comprovante de pagamento.
 * <p>
 * Define as operações de consulta e validação de transações na API
 * do gateway de pagamento, retornando dados reais para composição
 * de comprovantes confiáveis.
 */
public interface ReceiptService {

    /**
     * Consulta um pagamento pelo seu ID no Mercado Pago e retorna
     * o extrato completo com dados reais validados.
     * <p>
     * Se o pagamento não existir ou a API estiver indisponível,
     * retorna um {@link PaymentReceipt} com {@code validated = false}
     * e uma mensagem explicativa.
     *
     * @param paymentId Identificador numérico do pagamento no Mercado Pago.
     * @return Extrato com os dados reais da transação.
     */
    PaymentReceipt getReceipt(String paymentId);

    /**
     * Consulta um pagamento pelo seu ID e valida se ele pertence
     * à referência externa informada (ex: AGEND-123 ou MED-456).
     * <p>
     * Útil para garantir que um usuário só acesse comprovantes
     * de pagamentos que lhe pertencem.
     *
     * @param paymentId   Identificador numérico do pagamento no Mercado Pago.
     * @param referenceId Referência externa esperada para validação de propriedade.
     * @return Extrato validado, incluindo verificação de propriedade.
     */
    PaymentReceipt getReceiptWithOwnershipCheck(String paymentId, String referenceId);
}
