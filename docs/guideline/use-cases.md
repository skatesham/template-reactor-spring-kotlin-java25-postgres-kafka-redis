# Use Cases

Cada caso de uso deve representar uma intenção explícita da aplicação.

Exemplo:

```text
<context>/application/usecase/confirm/
├── ConfirmOrderCommand.kt
├── ConfirmOrder.kt
└── ConfirmOrderResult.kt
```

## Responsabilidade

Um use case deve:

1. validar condições de entrada que pertencem à aplicação;
2. carregar aggregates necessários;
3. chamar comportamento do domínio;
4. persistir alterações;
5. coordenar ports externos;
6. retornar um resultado.

## Exemplo

```kotlin
@Service
class ConfirmOrder(
    private val repository: OrderRepository,
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

## Transaction Boundary

Preferir `@Transactional` no use case.

Evitar transação no Controller.

## Command vs Query

```text
Command → altera estado
Query   → consulta estado
```

CQRS distribuído não é requisito.

Queries complexas podem utilizar projeções otimizadas sem reconstruir aggregates completos.
