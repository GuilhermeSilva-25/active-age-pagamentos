package com.activeage.payment.model;

/**
 * Enumera os tipos de produtos ou serviços que podem ser pagos no ecossistema.
 *
 * Auxilia na identificação da finalidade do pagamento, distinguindo por exemplo
 * entre o pagamento de uma consulta avulsa e uma assinatura de plano.
 */
public enum PaymentType {
    CONSULTATION,
    SUBSCRIPTION
}