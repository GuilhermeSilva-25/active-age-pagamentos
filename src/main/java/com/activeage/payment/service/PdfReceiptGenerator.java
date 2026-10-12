package com.activeage.payment.service;

import com.activeage.payment.model.PaymentReceipt;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.draw.LineSeparator;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;

/**
 * Gerador de comprovantes/extratos de pagamento em formato PDF.
 * <p>
 * Utiliza a biblioteca OpenPDF (fork livre do iText) para gerar
 * documentos profissionais com a identidade visual do Active Age.
 * <p>
 * O documento gerado NÃO utiliza logotipos, selos ou elementos que
 * sugiram emissão oficial pelo Mercado Pago. Contém um aviso claro
 * de que foi emitido pelo sistema Active Age.
 */
@Component
public class PdfReceiptGenerator {

    /** Cor primária verde (igual à imagem de referência) */
    private static final Color COR_VERDE_PRIMARIA = new Color(126, 179, 129); // #7EB381

    /** Cor verde escuro para destaque de valor pago */
    private static final Color COR_VERDE_DESTAQUE = new Color(46, 139, 87); // #2E8B57

    /** Cor do texto escuro */
    private static final Color COR_TEXTO_ESCURO = new Color(40, 40, 40);

    /** Cor do texto atenuado (cinza) */
    private static final Color COR_TEXTO_CINZA = new Color(110, 110, 110);

    /** Cor de fundo para blocos destacados */
    private static final Color COR_FUNDO_DESTAQUE = new Color(248, 250, 248);

    /** Formatador de data/hora no padrão brasileiro */
    private static final DateTimeFormatter FORMATO_DATA_HORA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");

    /** Formatador de data (sem hora) */
    private static final DateTimeFormatter FORMATO_DATA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * Gera o PDF do comprovante/extrato a partir dos dados reais
     * do pagamento obtidos da API do Mercado Pago e do Active Age.
     *
     * @param receipt Dados reais do pagamento validados.
     * @return Array de bytes contendo o documento PDF.
     * @throws DocumentException Se ocorrer erro na geração do documento.
     */
    public byte[] gerar(PaymentReceipt receipt) throws DocumentException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 40, 40, 40, 40);
        PdfWriter.getInstance(document, baos);
        document.open();

        // Faixa de cabeçalho verde institucional
        adicionarFaixaCabecalho(document);

        // Cabeçalho institucional do consultório virtual
        adicionarCabecalhoInstitucional(document);

        // Separador visual fino
        document.add(new LineSeparator(0.8f, 95f, new Color(220, 220, 220), Element.ALIGN_CENTER, -1));
        document.add(Chunk.NEWLINE);

        // Tabela de dados (médico, CRM, paciente, datas, transação)
        adicionarDadosConsultaETransacao(document, receipt);

        // Bloco destacado de VALOR PAGO
        adicionarBlocoValorPago(document, receipt);

        // Rodapé com aviso legal
        adicionarRodape(document, receipt);

        document.close();
        return baos.toByteArray();
    }

    /**
     * Adiciona faixa verde no topo com o título "Recibo de Consulta".
     */
    private void adicionarFaixaCabecalho(Document document) throws DocumentException {
        PdfPTable headerTable = new PdfPTable(1);
        headerTable.setWidthPercentage(100);

        Font fonteFaixa = new Font(Font.HELVETICA, 13, Font.BOLD, Color.WHITE);
        PdfPCell celulaFaixa = new PdfPCell(new Phrase("Recibo de Consulta", fonteFaixa));
        celulaFaixa.setBackgroundColor(COR_VERDE_PRIMARIA);
        celulaFaixa.setPaddingTop(10);
        celulaFaixa.setPaddingBottom(10);
        celulaFaixa.setPaddingLeft(15);
        celulaFaixa.setBorder(Rectangle.NO_BORDER);

        headerTable.addCell(celulaFaixa);
        headerTable.setSpacingAfter(15);
        document.add(headerTable);
    }

    /**
     * Adiciona o cabeçalho institucional: Logo oficial Active Age + Active Age Consultório Virtual.
     */
    private void adicionarCabecalhoInstitucional(Document document) throws DocumentException {
        try {
            // Logo oficial Active Age enviada pelo usuário (em base64)
            String logoBase64 = "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";
            byte[] imageBytes = java.util.Base64.getDecoder().decode(logoBase64);
            Image logo = Image.getInstance(imageBytes);
            logo.scaleToFit(110, 36);
            logo.setAlignment(Element.ALIGN_CENTER);
            logo.setSpacingAfter(6);
            document.add(logo);
        } catch (Exception ignored) {
            // Se falhar o carregamento da imagem, o texto institucional abaixo é exibido normalmente
        }

        Font fonteTitulo = new Font(Font.HELVETICA, 13, Font.BOLD, COR_TEXTO_ESCURO);
        Paragraph titulo = new Paragraph("Active Age Consultório Virtual", fonteTitulo);
        titulo.setAlignment(Element.ALIGN_CENTER);
        document.add(titulo);

        Font fonteSubtitulo = new Font(Font.HELVETICA, 9, Font.NORMAL, COR_TEXTO_CINZA);
        Paragraph subtitulo = new Paragraph("Comprovante de Agendamento Eletrônico", fonteSubtitulo);
        subtitulo.setAlignment(Element.ALIGN_CENTER);
        subtitulo.setSpacingAfter(10);
        document.add(subtitulo);
    }

    /**
     * Adiciona a tabela com os dados organizados em linhas (rótulo à esquerda, valor à direita).
     */
    private void adicionarDadosConsultaETransacao(Document document, PaymentReceipt receipt) throws DocumentException {
        PdfPTable tabela = new PdfPTable(2);
        tabela.setWidthPercentage(95);
        tabela.setWidths(new float[]{38, 62});
        tabela.setSpacingBefore(5);
        tabela.setSpacingAfter(15);

        // ID da Transação (MP)
        adicionarLinhaItem(tabela, "ID da Transação (MP):",
                receipt.mercadoPagoId() != null ? receipt.mercadoPagoId().toString() : "Não informado");

        // Referência Active Age
        if (receipt.externalReference() != null && !receipt.externalReference().isBlank()) {
            adicionarLinhaItem(tabela, "Referência Active Age:", receipt.externalReference());
        }

        // Descrição
        if (receipt.description() != null && !receipt.description().isBlank()) {
            adicionarLinhaItem(tabela, "Descrição:", receipt.description());
        }

        // Médico (se houver)
        if (receipt.medicoNome() != null && !receipt.medicoNome().isBlank()) {
            adicionarLinhaItem(tabela, "Médico:", receipt.medicoNome());
        }

        // CRM do Médico (se houver)
        if (receipt.medicoCrm() != null && !receipt.medicoCrm().isBlank()) {
            adicionarLinhaItem(tabela, "CRM:", receipt.medicoCrm());
        }

        // Paciente (se houver)
        if (receipt.pacienteNome() != null && !receipt.pacienteNome().isBlank()) {
            adicionarLinhaItem(tabela, "Paciente:", receipt.pacienteNome());
        }

        // Data da Consulta (se houver)
        if (receipt.dataConsulta() != null) {
            adicionarLinhaItem(tabela, "Data da Consulta:", receipt.dataConsulta().format(FORMATO_DATA_HORA));
        }

        // Valor Pago
        String moeda = receipt.currencyId() != null ? receipt.currencyId() : "BRL";
        String simboloMoeda = "BRL".equalsIgnoreCase(moeda) ? "R$" : moeda;
        if (receipt.transactionAmount() != null) {
            adicionarLinhaItem(tabela, "Valor Pago:", String.format("%s %.2f", simboloMoeda, receipt.transactionAmount()));
        }

        // Valor Líquido Recebido
        if (receipt.netReceivedAmount() != null) {
            adicionarLinhaItem(tabela, "Valor Líquido Recebido:", String.format("%s %.2f", simboloMoeda, receipt.netReceivedAmount()));
        }

        // Situação do Pagamento
        adicionarLinhaItem(tabela, "Situação do Pagamento:", traduzirStatus(receipt.status()));

        // Detalhe
        if (receipt.statusDetail() != null && !receipt.statusDetail().isBlank()) {
            adicionarLinhaItem(tabela, "Detalhe:", traduzirStatusDetail(receipt.statusDetail()));
        }

        // Data da Transação
        if (receipt.dateCreated() != null) {
            adicionarLinhaItem(tabela, "Data da Transação:", receipt.dateCreated().format(FORMATO_DATA_HORA));
        }

        // Data de Aprovação
        if (receipt.dateApproved() != null) {
            adicionarLinhaItem(tabela, "Data de Aprovação:", receipt.dateApproved().format(FORMATO_DATA_HORA));
        }

        // Método de Pagamento
        adicionarLinhaItem(tabela, "Método de Pagamento:",
                traduzirMetodoPagamento(receipt.paymentMethodId(), receipt.paymentTypeId()));

        // Parcelas
        if (receipt.installments() != null) {
            String parcelasTxt = receipt.installments() > 1 ? receipt.installments() + "x" : "À vista (1x)";
            adicionarLinhaItem(tabela, "Parcelas:", parcelasTxt);
        }

        // Seção DADOS DO PAGADOR
        String nomePagador = receipt.payerFirstName() != null
                ? (receipt.payerFirstName() + (receipt.payerLastName() != null ? " " + receipt.payerLastName() : "")).trim()
                : (receipt.pacienteNome() != null ? receipt.pacienteNome() : null);

        boolean temDadosPagador = nomePagador != null || receipt.payerEmail() != null || receipt.payerIdentificationNumber() != null;
        if (temDadosPagador) {
            adicionarDivisorSecao(tabela, "DADOS DO PAGADOR");

            if (nomePagador != null && !nomePagador.isBlank()) {
                adicionarLinhaItem(tabela, "Nome:", nomePagador);
            }
            if (receipt.payerEmail() != null && !receipt.payerEmail().isBlank()) {
                adicionarLinhaItem(tabela, "E-mail:", receipt.payerEmail());
            }
            if (receipt.payerIdentificationNumber() != null && !receipt.payerIdentificationNumber().isBlank()) {
                String labelDoc = (receipt.payerIdentificationType() != null && !receipt.payerIdentificationType().isBlank())
                        ? receipt.payerIdentificationType() + ":"
                        : "Documento/CRM:";
                adicionarLinhaItem(tabela, labelDoc, receipt.payerIdentificationNumber());
            } else if (receipt.medicoCrm() != null && !receipt.medicoCrm().isBlank()) {
                adicionarLinhaItem(tabela, "CRM:", receipt.medicoCrm());
            }
        }

        document.add(tabela);
    }

    /**
     * Adiciona um cabeçalho de seção dentro da tabela de dados.
     */
    private void adicionarDivisorSecao(PdfPTable tabela, String tituloSecao) {
        Font fonteSecao = new Font(Font.HELVETICA, 8, Font.BOLD, COR_VERDE_DESTAQUE);
        PdfPCell cellSecao = new PdfPCell(new Phrase(tituloSecao, fonteSecao));
        cellSecao.setColspan(2);
        cellSecao.setBorder(Rectangle.NO_BORDER);
        cellSecao.setPaddingTop(10);
        cellSecao.setPaddingBottom(4);
        tabela.addCell(cellSecao);
    }

    /**
     * Adiciona o bloco destacado com VALOR PAGO em verde.
     */
    private void adicionarBlocoValorPago(Document document, PaymentReceipt receipt) throws DocumentException {
        PdfPTable boxTable = new PdfPTable(1);
        boxTable.setWidthPercentage(95);

        PdfPCell boxCell = new PdfPCell();
        boxCell.setBackgroundColor(COR_FUNDO_DESTAQUE);
        boxCell.setBorderColor(new Color(225, 235, 225));
        boxCell.setPaddingTop(12);
        boxCell.setPaddingBottom(12);
        boxCell.setHorizontalAlignment(Element.ALIGN_CENTER);

        Font fonteLabel = new Font(Font.HELVETICA, 9, Font.BOLD, COR_TEXTO_CINZA);
        Paragraph label = new Paragraph("VALOR PAGO", fonteLabel);
        label.setAlignment(Element.ALIGN_CENTER);
        boxCell.addElement(label);

        String moeda = receipt.currencyId() != null ? receipt.currencyId() : "BRL";
        String simboloMoeda = "BRL".equalsIgnoreCase(moeda) ? "R$" : moeda;
        String valorFormatado = receipt.transactionAmount() != null
                ? String.format("%s %.2f", simboloMoeda, receipt.transactionAmount())
                : "R$ 0,00";

        Font fonteValor = new Font(Font.HELVETICA, 18, Font.BOLD, COR_VERDE_DESTAQUE);
        Paragraph valor = new Paragraph(valorFormatado, fonteValor);
        valor.setAlignment(Element.ALIGN_CENTER);
        boxCell.addElement(valor);

        boxTable.addCell(boxCell);
        boxTable.setSpacingAfter(15);
        document.add(boxTable);
    }

    /**
     * Adiciona uma linha simples na tabela (rótulo à esquerda, valor à direita).
     */
    private void adicionarLinhaItem(PdfPTable tabela, String rotulo, String valor) {
        Font fonteRotulo = new Font(Font.HELVETICA, 10, Font.NORMAL, COR_TEXTO_CINZA);
        Font fonteValor = new Font(Font.HELVETICA, 10, Font.BOLD, COR_TEXTO_ESCURO);

        PdfPCell cellRotulo = new PdfPCell(new Phrase(rotulo, fonteRotulo));
        cellRotulo.setBorder(Rectangle.NO_BORDER);
        cellRotulo.setPaddingTop(5);
        cellRotulo.setPaddingBottom(5);
        cellRotulo.setHorizontalAlignment(Element.ALIGN_LEFT);

        PdfPCell cellValor = new PdfPCell(new Phrase(valor != null ? valor : "Não informado", fonteValor));
        cellValor.setBorder(Rectangle.NO_BORDER);
        cellValor.setPaddingTop(5);
        cellValor.setPaddingBottom(5);
        cellValor.setHorizontalAlignment(Element.ALIGN_RIGHT);

        tabela.addCell(cellRotulo);
        tabela.addCell(cellValor);
    }

    /**
     * Adiciona o rodapé com aviso legal e informações do sistema emissor.
     */
    private void adicionarRodape(Document document, PaymentReceipt receipt) throws DocumentException {
        document.add(Chunk.NEWLINE);
        document.add(new LineSeparator(0.5f, 100f, Color.LIGHT_GRAY, Element.ALIGN_CENTER, -2));

        Font fonteAviso = new Font(Font.HELVETICA, 8, Font.ITALIC, Color.GRAY);

        Paragraph aviso = new Paragraph(
                "Este documento foi gerado pelo sistema Active Age com base nos dados "
                        + "registrados e validados junto à API do Mercado Pago. "
                        + "Não constitui documento fiscal e não foi emitido pelo Mercado Pago.",
                fonteAviso
        );
        aviso.setAlignment(Element.ALIGN_CENTER);
        aviso.setSpacingBefore(10);
        document.add(aviso);

        if (receipt.validated()) {
            Paragraph validacao = new Paragraph(
                    "Situação: " + receipt.validationMessage(),
                    fonteAviso
            );
            validacao.setAlignment(Element.ALIGN_CENTER);
            validacao.setSpacingBefore(3);
            document.add(validacao);
        } else {
            Font fonteAlerta = new Font(Font.HELVETICA, 9, Font.BOLD, new Color(180, 0, 0));
            Paragraph alerta = new Paragraph(
                    "⚠ " + receipt.validationMessage(),
                    fonteAlerta
            );
            alerta.setAlignment(Element.ALIGN_CENTER);
            alerta.setSpacingBefore(5);
            document.add(alerta);
        }

        // Data de emissão do documento
        Font fonteEmissao = new Font(Font.HELVETICA, 8, Font.NORMAL, Color.GRAY);
        Paragraph emissao = new Paragraph(
                "Documento emitido em: "
                        + java.time.LocalDateTime.now().format(FORMATO_DATA_HORA)
                        + " | Sistema Active Age — Pagamentos",
                fonteEmissao
        );
        emissao.setAlignment(Element.ALIGN_CENTER);
        emissao.setSpacingBefore(8);
        document.add(emissao);
    }


    /**
     * Retorna o valor fornecido ou "Não informado" se for nulo/vazio.
     */
    private String valorOuNaoInformado(String valor) {
        return (valor != null && !valor.isBlank()) ? valor : "Não informado";
    }

    /**
     * Traduz o status do Mercado Pago para português.
     */
    private String traduzirStatus(String status) {
        if (status == null) return "Não informado";
        return switch (status.toLowerCase()) {
            case "approved" -> "✅ Aprovado";
            case "pending" -> "⏳ Pendente";
            case "in_process" -> "⏳ Em processamento";
            case "rejected" -> "❌ Rejeitado";
            case "cancelled" -> "❌ Cancelado";
            case "refunded" -> "↩ Reembolsado";
            case "charged_back" -> "↩ Estornado (Chargeback)";
            case "in_mediation" -> "⚖ Em mediação";
            default -> status;
        };
    }

    /**
     * Traduz o detalhe do status para português.
     */
    private String traduzirStatusDetail(String detail) {
        if (detail == null) return "";
        return switch (detail.toLowerCase()) {
            case "accredited" -> "Pagamento creditado na conta";
            case "pending_contingency" -> "Processamento pendente (contingência)";
            case "pending_review_manual" -> "Pagamento em análise manual";
            case "cc_rejected_other_reason" -> "Cartão recusado";
            case "cc_rejected_call_for_authorize" -> "Cartão recusado — ligar para o banco";
            case "cc_rejected_insufficient_amount" -> "Saldo insuficiente no cartão";
            case "cc_rejected_bad_filled_security_code" -> "Código de segurança inválido";
            case "cc_rejected_bad_filled_date" -> "Data de validade inválida";
            case "cc_rejected_bad_filled_other" -> "Dados do cartão incorretos";
            default -> detail;
        };
    }

    /**
     * Traduz o método de pagamento do Mercado Pago para português.
     */
    private String traduzirMetodoPagamento(String metodo, String tipo) {
        if (metodo == null && tipo == null) return "Não informado";

        String descricaoMetodo = "";
        if (metodo != null) {
            descricaoMetodo = switch (metodo.toLowerCase()) {
                case "pix" -> "PIX";
                case "visa" -> "Visa";
                case "master", "mastercard" -> "Mastercard";
                case "elo" -> "Elo";
                case "amex" -> "American Express";
                case "hipercard" -> "Hipercard";
                case "bolbradesco" -> "Boleto Bradesco";
                case "account_money" -> "Saldo Mercado Pago";
                default -> metodo;
            };
        }

        String descricaoTipo = "";
        if (tipo != null) {
            descricaoTipo = switch (tipo.toLowerCase()) {
                case "credit_card" -> "Cartão de Crédito";
                case "debit_card" -> "Cartão de Débito";
                case "bank_transfer" -> "Transferência Bancária";
                case "ticket" -> "Boleto";
                case "account_money" -> "Saldo em Conta";
                default -> tipo;
            };
        }

        if (!descricaoMetodo.isBlank() && !descricaoTipo.isBlank()) {
            return descricaoMetodo + " (" + descricaoTipo + ")";
        }
        return !descricaoMetodo.isBlank() ? descricaoMetodo : descricaoTipo;
    }
}
