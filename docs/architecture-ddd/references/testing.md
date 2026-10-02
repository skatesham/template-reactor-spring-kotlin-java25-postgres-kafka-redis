# Testes

A estratégia segue as fronteiras arquiteturais.

## Domain

- unit tests;
- sem Spring;
- sem banco;
- preferencialmente sem mocks.

## Application

- unit tests;
- ports substituídos por fakes/mocks quando necessário.

## Persistence

- integração com PostgreSQL real via Testcontainers.

## Redis

- integração via Testcontainers.

## Kafka

- integração quando necessário.

## REST

- Spring MVC tests;
- validação de contratos HTTP.

Regra principal:

```text
quanto mais próximo do domínio,
menos infraestrutura deve ser necessária para testar.
```

## Arquitetura e OpenAPI

`ArchitectureTests` inspeciona dependências no bytecode de produção para verificar
domínio independente, aplicação sem infraestrutura/interfaces, REST sem acesso
a persistência/modelos de domínio, acesso entre contextos pela API de aplicação
e ausência de ciclos. Executar sem Docker:

```bash
./gradlew test --tests 'com.kotlin.template.architecture.ArchitectureTests'
```

`RestAssuredIntegrationTests` também consulta `/v3/api-docs` da aplicação real e
verifica os contratos de customer: schemas, descrições, parâmetros, erros,
autorização administrativa e ausência de corpo em respostas 202/204.
