# 💳 Active Age — Payment Microservice
 
<p align="center">
<img src="https://img.shields.io/badge/Deploy-Render-black?style=for-the-badge&logo=render" alt="Render Deploy" />
<img src="https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=java&logoColor=white" alt="Java 21" />
<img src="https://img.shields.io/badge/Spring_Boot-3-6DB33F?style=for-the-badge&logo=spring-boot&logoColor=white%22 alt="Spring Boot 3" />
<img src="https://img.shields.io/badge/Mercado_Pago-00B1EA?style=for-the-badge&logo=mercado-pago&logoColor=white%22 alt="Mercado Pago" />
<img src="https://img.shields.io/badge/Docker-2496ED?style=for-the-badge&logo=docker&logoColor=white" alt="Docker" />
</p>
 
---
 
## 🌐 Microserviço em Produção
 
Acesse o Endpoint base hospedado na nuvem:  
👉 **[https://active-age-payment-service.onrender.com](https://active-age-payment-service.onrender.com)**
 
---
 
## 💡 Sobre o Projeto
 
O **Payment Microservice** é um serviço backend completamente isolado, projetado com foco estrito no Princípio de Responsabilidade Única (SOLID). Sua única função é orquestrar integrações financeiras entre o ecossistema Active Age e os gateways de pagamento.
 
Ao separar a lógica financeira do Backend Principal (Core API), nós garantimos maior segurança, escalabilidade independente e evitamos que falhas no processamento de pagamentos derrubem o sistema de saúde.
 
---
 
## ✨ Principais Funcionalidades
 
### 🛒 Checkout Pro Integrado
- **Geração de Cobranças Dinâmicas:** Recebe os dados de planos mensais ou anuais do Frontend e aciona o SDK oficial do Mercado Pago para gerar links seguros de pagamento, permitindo transações via PIX e Cartões de Crédito.
 
### 📡 Webhook Router (Comunicação Assíncrona)
- **Escuta Ativa:** Endpoint configurado (`/api/payments/webhook`) para receber notificações instantâneas ("IPN") do servidor do Mercado Pago sempre que o status de uma fatura é alterado.
- **Server-to-Server Request:** Quando um pagamento é aprovado, o microserviço atua como um roteador de eventos, utilizando o `HttpClient` nativo do Java para disparar uma requisição HTTP silenciosa e segura para a API Principal, liberando o consultório virtual do médico.
 
### 🐳 Containerização Otimizada
- **Multi-stage Build:** Deploy configurado via `Dockerfile` otimizado, que compila a aplicação no Maven e roda uma imagem limpa e extremamente leve do `Eclipse Temurin 21 JRE`, economizando recursos na nuvem.
 
---
 
## 🛠️ Tecnologias Utilizadas
 
- **Linguagem:** [Java 21](https://www.oracle.com/java/technologies/downloads/#java21)
- **Framework:** [Spring Boot 3](https://spring.io/projects/spring-boot)
- **Integração:** SDK Oficial do Mercado Pago para Java
- **Requisições Nativas:** `java.net.http.HttpClient` (Sem necessidade de bibliotecas de terceiros como Feign ou WebClient)
- **Infraestrutura:** [Docker](https://www.docker.com/)
 
---
 
## 📁 Estrutura de Pastas
 
## 📁 Estrutura de Pastas
 
```bash
payment-service/
├── src/
│   └── main/
│       ├── java/com/activeage/payment/
│       │   ├── controller/
│       │   │   └── PaymentController.java         # Recebe chamadas REST e eventos Webhook
│       │   ├── model/
│       │   │   ├── PaymentIntent.java             # Estrutura de dados enviada pelo Frontend
│       │   │   ├── WebhookNotification.java       # Estrutura do IPN recebida do Mercado Pago
│       │   │   └── ... (Result, Status, Type)     # Enums e retornos de operação
│       │   ├── service/
│       │   │   ├── PaymentService.java            # Interface base de serviços
│       │   │   └── MercadoPagoPaymentService.java # Lógica do SDK e chamadas HTTP pro Core API
│       │   └── PaymentApplication.java            # Inicialização do Spring Boot
│       └── resources/
│           └── application.properties             # Variáveis de ambiente e porta
├── Dockerfile                                     # Script de containerização Multi-stage
└── pom.xml                                        # Gerenciamento de dependências Maven
```
 
---
 
## ⚙️ Como Executar Localmente
 
### Pré-requisitos
- JDK 21 instalado
- Maven instalado
- Uma conta de Desenvolvedor no Mercado Pago (Chave de Acesso)
 
### Passos
1. Clone este repositório.
2. Na raiz do projeto, configure as variáveis de ambiente necessárias (no terminal ou arquivo `.env`):
> `MERCADO_PAGO_ACCESS_TOKEN=sua-chave-de-producao-ou-teste`
> `MAIN_BACKEND_URL=http://localhost:8080` (A URL do seu Backend Principal)
3. Execute a aplicação via terminal:
> `mvn spring-boot:run`
4. A API subirá no servidor embutido na porta `8081`.
 
*Dica:* Para testar Webhooks localmente, utilize uma ferramenta como o **Ngrok** para expor sua porta 8081 para a internet.
 
---
 
## 🔒 Segurança e Privacidade
 
- **Chaves de API Isoladas:** As credenciais financeiras não ficam expostas no Backend Principal, mitigando riscos em caso de brechas.
- **Validação de Assinatura:** Eventos de Webhook podem ser validados quanto à sua origem, garantindo que requisições falsas não consigam ativar assinaturas no sistema de saúde.
 
---
 
## 📄 Licença
 
Este projeto foi desenvolvido como um sistema acadêmico focado em inovação para saúde digital e arquitetura de microserviços.
