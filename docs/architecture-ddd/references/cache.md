# Redis e Cache

Redis pertence à infraestrutura.

Uso recomendado:

- cache;
- rate limiting;
- idempotency;
- counters;
- estado distribuído de curta duração;
- locks quando realmente necessários.

## Fluxo

```text
request
  ↓
application
  ↓
Redis
  ├── hit → return
  └── miss
       ↓
   PostgreSQL
       ↓
     Redis
```

## Port

```kotlin
interface OrderCache {
    fun get(id: OrderId): OrderSnapshot?
    fun put(order: OrderSnapshot)
    fun evict(id: OrderId)
}
```

Implementação:

```text
RedisOrderCache
```

Redis não deve conter regras de negócio nem substituir PostgreSQL como fonte de verdade sem decisão arquitetural
explícita.
