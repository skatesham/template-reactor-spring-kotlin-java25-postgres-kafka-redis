---
name: spring-kotlin
description: Implementar, refatorar e revisar aplicações Spring Boot com Kotlin, organizadas por bounded context, com domínio independente e ports/adapters. Usar em casos de uso, APIs REST, persistência JPA/PostgreSQL, Kafka, Redis e testes dessas aplicações.
---

# Spring Kotlin

Aplicar um monólito modular organizado por capacidades de negócio, com DDD,
ports/adapters e separação entre domínio, aplicação e tecnologias externas.
Usar esta skill tanto em implementação quanto em revisão, no escopo solicitado.

## Entrada

1. Inspecionar o código e o build para identificar o package raiz, o contexto de
   negócio, as dependências disponíveis e o modelo de execução da aplicação.
   Não inferir que uma integração está configurada apenas por existir um starter.
2. Ler a referência de arquitetura ao criar ou alterar fronteiras entre camadas
   ou contextos. Carregar as demais referências conforme a tarefa, pela tabela.
3. Implementar a menor mudança que expresse a intenção de negócio. Usar apenas
   os patterns necessários; não criar camadas vazias, abstrações genéricas ou
   novos serviços distribuídos por padrão.
4. Escolher a validação proporcional à mudança e ao pedido. Ao concluir, informar
   o comportamento alterado, as verificações realizadas e limitações concretas.

## Organização e contratos

Neste template, usar `application/usecase/<intenção>/`, persistência em
`entity/`, `repository/` e `adapter/`, e REST em `request/` e `response/`,
sem package `dto`. Cada tipo principal público tem arquivo homônimo.
Ao criar ou alterar endpoints, manter o OpenAPI autoexplicativo e coerente
com o contrato real. Os detalhes estão nas referências de arquitetura e REST.

## Referências por tarefa

| Quando ler                                                               | Referência                                                |
|--------------------------------------------------------------------------|-----------------------------------------------------------|
| Estrutura, localização de classes e dependências entre camadas/contextos | [Arquitetura](references/architecture.md)                 |
| Aggregates, invariantes, value objects, IDs e patterns de domínio        | [Domínio](references/domain.md)                           |
| Kotlin/Java, nullability, tipos e injeção de dependências                | [Kotlin e Java](references/kotlin-java.md)                |
| Commands, queries, casos de uso, transações e consistência               | [Aplicação](references/application.md)                    |
| Controllers, DTOs, validação, erros e execução HTTP                      | [REST](references/rest.md)                                |
| Repositories, JPA, PostgreSQL e migrations Flyway                        | [Persistência](references/persistence.md)                 |
| Listeners, publicação e contratos de eventos Kafka                       | [Kafka](references/kafka.md)                              |
| Cache, TTL, invalidação e estado temporário Redis                        | [Redis](references/redis.md)                              |
| APIs externas, gateways e tradução de modelos de terceiros               | [Integrações](references/integrations.md)                 |
| Autorização, segredos ou coleta, exposição e retenção de dados pessoais  | [Segurança e privacidade](references/security-privacy.md) |
| Testes de domínio, aplicação, HTTP ou adapters                           | [Testes](references/testing.md)                           |

As referências contêm orientações completas para seus próprios assuntos.
Não precisam de outros documentos para serem usadas. Todos os caminhos desta
entrada são relativos à pasta da skill; `<base-package>` e `<context>` são
placeholders a substituir pelos nomes reais da aplicação.
