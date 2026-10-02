# Application Layer

A camada `application` contém os casos de uso em `usecase/<intenção>/`,
as dependências em `port/`, contratos de integração em `contract/`,
resultados compartilhados em `result/` e exceções em `exception/`.
Commands, queries e resultados exclusivos ficam em arquivos próprios junto
ao caso de uso. Ver [Empacotamento](packaging.md).

Exemplos:

```text
CreateOrder
ConfirmOrder
CancelOrder
RegisterCustomer
ApprovePayment
```

Sua responsabilidade é orquestrar.

```text
input
  ↓
load aggregate
  ↓
domain behavior
  ↓
persist
  ↓
external effects/events
  ↓
result
```

Exemplo:

```kotlin
@Service
class ConfirmOrder(
    private val repository: OrderRepository
) {
    @Transactional
    fun execute(command: ConfirmOrderCommand) {
        val order = repository.findById(command.orderId)
            ?: throw OrderNotFound(command.orderId)

        order.confirm()
        repository.save(order)
    }
}
```

## Regra

A Application Layer coordena.

O Domain decide.

Regras de negócio que pertencem ao Aggregate não devem ser movidas para Use Cases apenas para simplificar entidades.
