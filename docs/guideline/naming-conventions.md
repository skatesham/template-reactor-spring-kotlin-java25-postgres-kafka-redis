# Naming Conventions

Use nomes da linguagem de negócio.

## Use Cases

Preferir verbos explícitos:

```text
CreateOrder
ConfirmOrder
CancelOrder
RegisterCustomer
ApprovePayment
```

Evitar:

```text
OrderManager
OrderProcessor
GenericService
BaseService
```

## Commands

```text
CreateOrderCommand
ConfirmOrderCommand
RegisterCustomerCommand
```

## Queries

```text
FindOrderQuery
SearchOrdersQuery
GetCustomerQuery
```

## Results

```text
CreateOrderResult
OrderDetails
CustomerSummary
```

## Repositories

Port:

```text
OrderRepository
CustomerRepository
```

Adapter:

```text
JpaOrderRepository
JpaCustomerRepository
```

Spring Data interno:

```text
SpringDataOrderRepository
SpringDataCustomerRepository
```

## Persistence

```text
OrderJpaEntity
CustomerJpaEntity
```

## REST

```text
CreateOrderRequest
OrderResponse
OrderController
```

## Kafka

```text
OrderConfirmedMessage
OrderKafkaListener
KafkaOrderEventPublisher
```

## Redis

```text
OrderCache
RedisOrderCache
```

Evitar nomes genéricos como:

```text
Utils
Helper
CommonService
Manager
Processor
Handler
```

quando existe um nome de negócio mais preciso.
