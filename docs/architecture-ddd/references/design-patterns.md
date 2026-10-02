# Padrões de Projeto

## Repository

O domínio define a abstração:

```kotlin
interface OrderRepository {
    fun findById(id: OrderId): Order?
    fun save(order: Order): Order
}
```

A infraestrutura implementa.

## Factory

Usar quando a criação do Aggregate possui regras próprias.

```kotlin
object OrderFactory {
    fun create(customerId: CustomerId): Order = ...
}
```

## Strategy

Usar para comportamentos intercambiáveis.

```kotlin
interface ShippingStrategy {
    fun calculate(order: Order): Money
}
```

## Specification

Representa regras combináveis e reutilizáveis.

```kotlin
interface Specification<T> {
    fun isSatisfiedBy(value: T): Boolean
}
```

## Adapter

Implementações técnicas adaptam interfaces internas.

```text
OrderRepository
    ↑
JpaOrderRepository
```

```text
PaymentGateway
    ↑
HttpPaymentGateway
```

## Facade

Bounded contexts podem expor uma facade pública para evitar acesso aos seus componentes internos.

## Command / Query

Commands alteram estado.

Queries consultam estado.

Não é necessário implementar CQRS distribuído.

## Domain Event

Representa acontecimentos importantes do domínio.

```text
OrderConfirmed
CustomerRegistered
PaymentApproved
```
