# Kafka e Eventos

Kafka pertence à infraestrutura.

## Domain Event

Evento interno ao bounded context:

```text
OrderConfirmed
```

## Integration Event

Contrato externo:

```text
order.confirmed.v1
```

Esses objetos não precisam ser os mesmos.

## Fluxo

```text
Aggregate
  ↓
Domain Event
  ↓
Application
  ↓
Publisher Port
  ↓
Kafka Adapter
```

O domínio não conhece `KafkaTemplate`, `ProducerRecord` ou `@KafkaListener`.

## Consumer

Listeners devem apenas adaptar a mensagem para um caso de uso.

```kotlin
@KafkaListener(topics = ["payment.confirmed"])
fun consume(message: PaymentConfirmedMessage): Mono<Void> =
    confirmPayment.execute(ConfirmPaymentCommand(message.paymentId))
```

Neste template, o container subscreve o `Mono` do listener e confirma o offset após
sua conclusão. `max.poll.records=1` preserva a ordem por partição. Retry e envio
para a DLT compõem o mesmo publisher; falha da DLT mantém o offset original.
O produtor adapta a future Kafka com `Mono.fromFuture`; a chamada de envio fica
em `boundedElastic` porque a API do cliente pode aguardar metadata/buffer.
