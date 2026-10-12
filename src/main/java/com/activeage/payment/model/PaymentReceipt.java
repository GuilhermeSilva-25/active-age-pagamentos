package com.activeage.payment.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Representa o extrato/comprovante de uma transação de pagamento.
 * <p>
 * Contém todos os dados reais retornados pela API do Mercado Pago,
 * validados e formatados para exibição ao usuário ou geração de PDF.
 * Campos opcionais podem ser {@code null} quando a informação não está
 * disponível na resposta da API.
 *
 * @param mercadoPagoId         Identificador da transação no Mercado Pago.
 * @param externalReference     Referência externa (ex: AGEND-123 ou MED-456).
 * @param description           Nome ou descrição do produto/serviço/agendamento.
 * @param transactionAmount     Valor efetivamente cobrado na transação.
 * @param netReceivedAmount     Valor líquido recebido (após taxas), quando disponível.
 * @param currencyId            Código da moeda (ex: BRL).
 * @param status                Status real do pagamento (ex: approved, pending, rejected).
 * @param statusDetail          Detalhe adicional do status (ex: accredited, pending_review).
 * @param dateCreated           Data e hora de criação da transação.
 * @param dateApproved          Data e hora de aprovação (null se não aprovado).
 * @param paymentMethodId       Identificador do método de pagamento (ex: pix, visa, master).
 * @param paymentTypeId         Tipo do pagamento (ex: credit_card, debit_card, bank_transfer).
 * @param installments          Número de parcelas (null quando não aplicável).
 * @param payerEmail            E-mail do pagador, quando disponível.
 * @param payerFirstName        Primeiro nome do pagador, quando disponível.
 * @param payerLastName         Sobrenome do pagador, quando disponível.
 * @param medicoNome            Nome do médico responsável (quando consulta).
 * @param medicoCrm             CRM do médico responsável.
 * @param pacienteNome          Nome do paciente atendido.
 * @param dataConsulta          Data e hora da consulta agendada.
 * @param validated             Indica se a transação foi validada com sucesso na API do MP.
 * @param validationMessage     Mensagem descritiva sobre o resultado da validação.
 */
public record PaymentReceipt(
        Long mercadoPagoId,
        String externalReference,
        String description,
        BigDecimal transactionAmount,
        BigDecimal netReceivedAmount,
        String currencyId,
        String status,
        String statusDetail,
        LocalDateTime dateCreated,
        LocalDateTime dateApproved,
        String paymentMethodId,
        String paymentTypeId,
        Integer installments,
        String payerEmail,
        String payerFirstName,
        String payerLastName,
        String payerIdentificationType,
        String payerIdentificationNumber,
        String medicoNome,
        String medicoCrm,
        String pacienteNome,
        LocalDateTime dataConsulta,
        boolean validated,
        String validationMessage
) {
    /**
     * Construtor de compatibilidade para chamadas sem os campos de consulta (médico, crm, paciente, dataConsulta).
     */
    public PaymentReceipt(
            Long mercadoPagoId,
            String externalReference,
            String description,
            BigDecimal transactionAmount,
            BigDecimal netReceivedAmount,
            String currencyId,
            String status,
            String statusDetail,
            LocalDateTime dateCreated,
            LocalDateTime dateApproved,
            String paymentMethodId,
            String paymentTypeId,
            Integer installments,
            String payerEmail,
            String payerFirstName,
            String payerLastName,
            String payerIdentificationType,
            String payerIdentificationNumber,
            boolean validated,
            String validationMessage
    ) {
        this(
                mercadoPagoId,
                externalReference,
                description,
                transactionAmount,
                netReceivedAmount,
                currencyId,
                status,
                statusDetail,
                dateCreated,
                dateApproved,
                paymentMethodId,
                paymentTypeId,
                installments,
                payerEmail,
                payerFirstName,
                payerLastName,
                payerIdentificationType,
                payerIdentificationNumber,
                null,
                null,
                null,
                null,
                validated,
                validationMessage
        );
    }
}
