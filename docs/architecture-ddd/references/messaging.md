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
fun consume(message: PaymentConfirmedMessage) {
    confirmPayment.execute(
        ConfirmPaymentCommand(message.paymentId)
    )
}
```
