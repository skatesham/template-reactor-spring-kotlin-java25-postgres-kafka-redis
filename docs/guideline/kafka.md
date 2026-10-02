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
fun consume(message: PaymentConfirmedMessage) {
    confirmPayment.execute(
        ConfirmPaymentCommand(message.paymentId)
    )
}
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
    fun publish(event: OrderConfirmed)
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
