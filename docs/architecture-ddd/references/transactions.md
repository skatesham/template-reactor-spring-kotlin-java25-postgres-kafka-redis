# Transações e Consistência

## Transaction Boundary

`@Transactional` deve ficar preferencialmente na Application Layer.

```kotlin
@Transactional
fun execute(command: ConfirmOrderCommand): Mono<Confirmation>
```

Evitar transações em Controllers.

## Transactional Outbox

Para eventos críticos:

Evitar:

```text
PostgreSQL commit
↓
Kafka publish
```

Preferir:

```text
Transaction
├── business data
└── outbox event

Commit
  ↓
Outbox Publisher
  ↓
Kafka
```

Use Outbox quando a perda ou inconsistência de eventos for relevante para o negócio.

Neste template, o transaction manager é R2DBC e acompanha o contexto Reactor.
Compor todas as operações SQL no publisher retornado pelo caso de uso. Cancelamento
ou erro provoca rollback; cache é invalidado com sincronização transacional reativa
após commit. Não usar `TransactionTemplate` bloqueante em pipelines reativos.
