# Kafka Guideline

Kafka é mecanismo de integração.

## Consumer

Listeners são adapters de entrada:

```text
<context>/interfaces/messaging/
```

Exemplo:

```kotlin
@KafkaListener(topics = ["payment.confirmed"])
fun consume(message: PaymentConfirmedMessage): Mono<Void> =
    confirmPayment.execute(ConfirmPaymentCommand(message.paymentId))
```

Listener deve adaptar e delegar.

Não colocar regra de negócio no listener.

## Publisher

Publisher Kafka é adapter de saída:

```text
<context>/infrastructure/messaging/
```

Application depende de uma porta:

```kotlin
interface OrderEventPublisher {
    fun publish(event: OrderConfirmed): Mono<Void>
}
```

Infrastructure implementa com Kafka.

## Contracts

Separar:

```text
Domain Event
Integration Event
Kafka Message
```

quando seus ciclos de evolução forem diferentes.

## Reliability

Para eventos críticos, considerar Transactional Outbox.

A decisão depende do impacto de inconsistência entre banco e broker.

Neste template, o container subscreve o `Mono` do listener e confirma o offset após
sua conclusão. `max.poll.records=1` preserva a ordem por partição. Retry e envio
para a DLT compõem o mesmo publisher; falha da DLT mantém o offset original.
O produtor adapta a future Kafka com `Mono.fromFuture`; a chamada de envio fica
em `boundedElastic` porque a API do cliente pode aguardar metadata/buffer.
