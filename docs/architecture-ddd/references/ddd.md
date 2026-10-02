# DDD e Bounded Contexts

## Bounded Context

A aplicação deve ser organizada por capacidades de negócio:

```text
customer/
order/
payment/
catalog/
identity/
```

Evitar estrutura global baseada apenas em tipos técnicos:

```text
controller/
service/
repository/
entity/
dto/
```

Cada contexto:

- possui seu próprio modelo de domínio;
- controla seus repositórios;
- define sua API pública;
- não expõe entidades de persistência;
- não acessa diretamente repositórios internos de outro contexto.

## Elementos do domínio

O `domain` pode conter:

- Aggregate Root
- Entity
- Value Object
- Domain Service
- Repository interface
- Domain Event
- Specification
- Factory
- Domain Exception

## Aggregate

O Aggregate Root protege as invariantes.

```kotlin
class Order(
    val id: OrderId,
    private var status: OrderStatus
) {
    fun confirm() {
        require(status == OrderStatus.DRAFT)
        status = OrderStatus.CONFIRMED
    }
}
```

Preferir:

```kotlin
order.confirm()
```

Evitar:

```kotlin
order.status = CONFIRMED
```

## Value Objects

Conceitos relevantes do domínio não devem ser representados apenas por primitivos.

```kotlin
@JvmInline
value class Email(val value: String)
```

Preferir `Email`, `Money`, `OrderId`, `Percentage`, etc.
