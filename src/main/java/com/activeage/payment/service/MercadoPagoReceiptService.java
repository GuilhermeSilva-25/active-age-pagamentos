package com.activeage.payment.service;

import com.activeage.payment.model.PaymentReceipt;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.payment.Payment;
import com.mercadopago.resources.payment.PaymentPayer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;

/**
 * Implementação do serviço de extrato/comprovante que consulta
 * a API oficial do Mercado Pago para obter dados reais da transação
 * e enriquece com os dados do agendamento/consulta do Active Age.
 * <p>
 * Nenhum dado é simulado ou inventado. Campos não disponíveis na
 * resposta da API são retornados como {@code null}.
 */
@Service
public class MercadoPagoReceiptService implements ReceiptService {

    @Value("${main-backend.url:https://active-age-backend.onrender.com}")
    private String mainBackendUrl;

    /**
     * Consulta o pagamento pelo ID na API do Mercado Pago e constrói
     * o extrato com todos os dados reais disponíveis.
     *
     * @param paymentId Identificador numérico do pagamento no Mercado Pago.
     * @return Extrato com dados reais, ou extrato com validated=false em caso de erro.
     */
    @Override
    public PaymentReceipt getReceipt(String paymentId) {
        // Validação do parâmetro de entrada
        if (paymentId == null || paymentId.isBlank()) {
            return criarExtratoInvalido(null,
                    "O identificador do pagamento não foi fornecido.");
        }

        // Limpa caracteres não numéricos para extrair apenas o ID do MP
        String cleanId = paymentId.replaceAll("[^0-9]", "");
        if (cleanId.isBlank()) {
            return criarExtratoInvalido(null,
                    "O identificador informado não contém um ID numérico válido do Mercado Pago: " + paymentId);
        }

        try {
            PaymentClient client = new PaymentClient();
            Payment payment = client.get(Long.parseLong(cleanId));

            if (payment == null) {
                return criarExtratoInvalido(Long.parseLong(cleanId),
                        "Nenhum pagamento encontrado com o ID informado.");
            }

            return montarExtrato(payment, true,
                    gerarMensagemValidacao(payment.getStatus()));

        } catch (MPApiException e) {
            // Erro retornado pela API do MP (ex: 404 Not Found, 401 Unauthorized)
            String detalhe = e.getApiResponse() != null ? e.getApiResponse().getContent() : e.getMessage();
            int statusCode = e.getStatusCode();

            if (statusCode == 404) {
                return criarExtratoInvalido(Long.parseLong(cleanId),
                        "Pagamento não encontrado na API do Mercado Pago (ID: " + cleanId + ").");
            }

            return criarExtratoInvalido(Long.parseLong(cleanId),
                    "Erro ao consultar a API do Mercado Pago (HTTP " + statusCode + "): " + detalhe);

        } catch (MPException e) {
            // Erro de conexão ou configuração do SDK
            return criarExtratoInvalido(Long.parseLong(cleanId),
                    "Falha na comunicação com o Mercado Pago. Tente novamente mais tarde. Detalhe: " + e.getMessage());

        } catch (NumberFormatException e) {
            return criarExtratoInvalido(null,
                    "O identificador informado não é um número válido: " + paymentId);
        }
    }

    /**
     * Consulta o pagamento e verifica se pertence à referência externa informada.
     * Impede que um usuário acesse comprovantes de pagamentos de terceiros.
     *
     * @param paymentId   Identificador numérico do pagamento no Mercado Pago.
     * @param referenceId Referência externa esperada para validação de propriedade.
     * @return Extrato validado com verificação de propriedade.
     */
    @Override
    public PaymentReceipt getReceiptWithOwnershipCheck(String paymentId, String referenceId) {
        PaymentReceipt receipt = getReceipt(paymentId);

        // Se a consulta já falhou, retorna sem verificação adicional
        if (!receipt.validated()) {
            return receipt;
        }

        // Verifica se a referência externa do pagamento corresponde à esperada
        if (referenceId != null && !referenceId.isBlank()) {
            String externalRef = receipt.externalReference();
            if (externalRef == null || !externalRef.equalsIgnoreCase(referenceId)) {
                return new PaymentReceipt(
                        receipt.mercadoPagoId(),
                        receipt.externalReference(),
                        null, null, null, null,
                        receipt.status(), null,
                        null, null, null, null, null,
                        null, null, null, null, null,
                        null, null, null, null,
                        false,
                        "O pagamento informado não pertence à referência solicitada (" + referenceId + ")."
                );
            }
        }

        return receipt;
    }

    /**
     * Monta o extrato completo a partir do objeto {@link Payment} retornado
     * pela API do Mercado Pago, enriquecendo com os dados do agendamento se for consulta.
     */
    PaymentReceipt montarExtrato(Payment payment, boolean validated, String mensagem) {
        // Extrai dados do pagador de forma segura
        String payerEmail = null;
        String payerFirstName = null;
        String payerLastName = null;
        String payerIdType = null;
        String payerIdNumber = null;

        PaymentPayer payer = payment.getPayer();
        if (payer != null) {
            payerEmail = payer.getEmail();
            payerFirstName = payer.getFirstName();
            payerLastName = payer.getLastName();

            if (payer.getIdentification() != null) {
                payerIdType = payer.getIdentification().getType();
                String fullNumber = payer.getIdentification().getNumber();
                // Mascara parcialmente o documento por segurança
                payerIdNumber = mascararDocumento(fullNumber);
            }
        }

        // Extrai valor líquido recebido, quando disponível
        BigDecimal netReceived = null;
        if (payment.getTransactionDetails() != null) {
            netReceived = payment.getTransactionDetails().getNetReceivedAmount();
        }

        // Converte datas do OffsetDateTime do SDK para LocalDateTime
        LocalDateTime dateCreated = payment.getDateCreated() != null
                ? payment.getDateCreated().toLocalDateTime()
                : null;
        LocalDateTime dateApproved = payment.getDateApproved() != null
                ? payment.getDateApproved().toLocalDateTime()
                : null;

        // Enriquece com dados do agendamento (médico, CRM, paciente, data da consulta)
        String medicoNome = null;
        String medicoCrm = null;
        String pacienteNome = null;
        LocalDateTime dataConsulta = null;

        String externalRef = payment.getExternalReference();
        if (externalRef != null && !externalRef.isBlank()) {
            AgendamentoInfo info = buscarDadosAgendamento(externalRef);
            if (info != null) {
                medicoNome = info.medicoNome();
                medicoCrm = info.medicoCrm();
                pacienteNome = info.pacienteNome();
                dataConsulta = info.dataHora();
            }
        }

        // Se o pacienteNome não veio do agendamento, usa nome do pagador caso disponível
        if (pacienteNome == null && (payerFirstName != null || payerLastName != null)) {
            pacienteNome = ((payerFirstName != null ? payerFirstName : "") + " "
                    + (payerLastName != null ? payerLastName : "")).trim();
        }

        return new PaymentReceipt(
                payment.getId(),
                payment.getExternalReference(),
                payment.getDescription(),
                payment.getTransactionAmount(),
                netReceived,
                payment.getCurrencyId(),
                payment.getStatus(),
                payment.getStatusDetail(),
                dateCreated,
                dateApproved,
                payment.getPaymentMethodId(),
                payment.getPaymentTypeId(),
                payment.getInstallments(),
                payerEmail,
                payerFirstName,
                payerLastName,
                payerIdType,
                payerIdNumber,
                medicoNome,
                medicoCrm,
                pacienteNome,
                dataConsulta,
                validated,
                mensagem
        );
    }

    private record AgendamentoInfo(String medicoNome, String medicoCrm, String pacienteNome, LocalDateTime dataHora) {}

    /**
     * Consulta o backend principal para buscar os dados completos do agendamento.
     */
    private AgendamentoInfo buscarDadosAgendamento(String referenceId) {
        if (referenceId == null || !referenceId.startsWith("AGEND-")) {
            return null;
        }

        try {
            String cleanId = referenceId.substring(6);
            HttpClient httpClient = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(mainBackendUrl + "/api/agendamentos/" + cleanId))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200 && response.body() != null && !response.body().isBlank()) {
                com.google.gson.JsonObject obj = com.google.gson.JsonParser.parseString(response.body()).getAsJsonObject();
                String mNome = obj.has("medicoNome") && !obj.get("medicoNome").isJsonNull() ? obj.get("medicoNome").getAsString() : null;
                String mCrm = obj.has("medicoCrm") && !obj.get("medicoCrm").isJsonNull() ? obj.get("medicoCrm").getAsString() : null;
                String pNome = obj.has("pacienteNome") && !obj.get("pacienteNome").isJsonNull() ? obj.get("pacienteNome").getAsString() : null;
                LocalDateTime dtConsulta = null;
                if (obj.has("dataHora") && !obj.get("dataHora").isJsonNull()) {
                    try {
                        dtConsulta = LocalDateTime.parse(obj.get("dataHora").getAsString());
                    } catch (Exception ignored) {}
                }
                return new AgendamentoInfo(mNome, mCrm, pNome, dtConsulta);
            }
        } catch (Exception e) {
            System.err.println("Aviso: Não foi possível obter dados do agendamento (" + referenceId + "): " + e.getMessage());
        }
        return null;
    }

    /**
     * Cria um extrato marcado como não validado, usado quando a consulta
     * à API falha ou o pagamento não é encontrado.
     */
    private PaymentReceipt criarExtratoInvalido(Long mercadoPagoId, String mensagem) {
        return new PaymentReceipt(
                mercadoPagoId,
                null, null, null, null, null,
                "not_found", null,
                null, null, null, null, null,
                null, null, null, null, null,
                null, null, null, null,
                false,
                mensagem
        );
    }

    /**
     * Gera uma mensagem descritiva de acordo com o status do pagamento.
     */
    private String gerarMensagemValidacao(String status) {
        if (status == null) {
            return "Status do pagamento não disponível.";
        }
        return switch (status.toLowerCase()) {
            case "approved" -> "Pagamento aprovado e confirmado pelo Mercado Pago.";
            case "pending" -> "Pagamento pendente de confirmação. Aguardando processamento.";
            case "in_process" -> "Pagamento em processamento. Aguarde a confirmação do Mercado Pago.";
            case "rejected" -> "Pagamento rejeitado pelo Mercado Pago. O valor não foi cobrado.";
            case "cancelled" -> "Pagamento cancelado. A transação não foi concluída.";
            case "refunded" -> "Pagamento reembolsado. O valor foi devolvido ao pagador.";
            case "charged_back" -> "Pagamento estornado (chargeback). O valor foi devolvido ao pagador.";
            case "in_mediation" -> "Pagamento em mediação. Disputa em andamento no Mercado Pago.";
            default -> "Status do pagamento: " + status;
        };
    }

    /**
     * Mascara parcialmente um número de documento por segurança.
     * Ex: "12345678901" → "***.***.789-01"
     */
    private String mascararDocumento(String numero) {
        if (numero == null || numero.length() < 4) {
            return numero;
        }
        int visivel = Math.min(4, numero.length());
        String parteVisivel = numero.substring(numero.length() - visivel);
        return "***" + parteVisivel;
    }
}
