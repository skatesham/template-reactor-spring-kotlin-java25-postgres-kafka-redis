# Transações e Consistência

## Transaction Boundary

`@Transactional` deve ficar preferencialmente na Application Layer.

```kotlin
@Transactional
fun execute(command: ConfirmOrderCommand)
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
