package com.activeage.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Classe principal responsável pela inicialização do microserviço de pagamentos.
 *
 * Utiliza o Spring Boot para levantar o servidor web embutido (ex: Tomcat)
 * e registrar todos os componentes e configurações necessários para a aplicação rodar.
 */
@SpringBootApplication
public class PaymentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }

}
