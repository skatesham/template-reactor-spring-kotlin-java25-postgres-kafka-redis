# Redis Guideline

Redis é infraestrutura.

Local:

```text
<context>/infrastructure/cache/
```

## Uso adequado

- cache;
- idempotency keys;
- rate limiting;
- counters;
- sessões quando aplicável;
- estado distribuído temporário.

## Port

Quando cache fizer parte do fluxo explícito da aplicação:

```kotlin
interface OrderCache {
    fun get(id: OrderId): OrderSnapshot?
    fun put(value: OrderSnapshot)
    fun evict(id: OrderId)
}
```

Adapter:

```text
RedisOrderCache
```

## Regras

- não colocar regra de negócio no cache;
- definir TTL explicitamente;
- não assumir que cache sempre existe;
- cache miss deve ser comportamento esperado;
- invalidar/atualizar cache após alteração da fonte de verdade;
- não guardar dados pessoais além do necessário;
- evitar cache de dados sensíveis sem necessidade concreta;
- nunca armazenar segredo em texto puro apenas porque Redis está em rede privada.
