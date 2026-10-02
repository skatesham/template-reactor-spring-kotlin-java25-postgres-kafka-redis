# Architecture

Este documento define o padrão arquitetural do projeto.

A arquitetura combina:

- Domain-Driven Design (DDD)
- Modular Monolith
- Package by Feature / Bounded Context
- Clean / Hexagonal Architecture
- Ports and Adapters
- CQRS leve
- Domain Events
- SOLID

## Estrutura principal

```text
src/main/kotlin/com/kotlin/template/
├── shared/
├── customer/
├── identity/
├── audit/
└── notification/
```

Cada bounded context segue:

```text
<context>/
├── domain/
├── application/
├── infrastructure/
└── interfaces/
```

A organização interna e as regras para arquivos, REST/OpenAPI e crescimento
estão em [Empacotamento e localização](references/packaging.md).

## Regra de dependência

```text
interfaces ───→ application ───→ domain
                     ↑
infrastructure ──────┘
```

O `domain` não depende de Spring, JPA, Kafka, Redis, HTTP ou qualquer tecnologia externa.

## Documentação detalhada

- [DDD e Bounded Contexts](references/ddd.md)
- [Empacotamento e Estrutura](references/packaging.md)
- [Padrões de Projeto](references/design-patterns.md)
- [Application Layer e Use Cases](references/application-layer.md)
- [Persistência, JPA e PostgreSQL](references/persistence.md)
- [Redis e Cache](references/cache.md)
- [Kafka e Eventos](references/messaging.md)
- [REST e DTOs](references/interfaces.md)
- [Integrações e Ports/Adapters](references/integrations.md)
- [Transações e Consistência](references/transactions.md)
- [Testes](references/testing.md)
- [Regras Arquiteturais](references/rules.md)

## Princípio central

```text
Business first.
Framework second.
```

O domínio representa o negócio. Spring fornece a infraestrutura para executá-lo.
