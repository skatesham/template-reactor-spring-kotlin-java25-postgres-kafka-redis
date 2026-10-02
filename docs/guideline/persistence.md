# Persistence Guideline

Persistência fica em:

```text
<context>/infrastructure/persistence/
```

## Separação

```text
Domain model
    ↕ mapper
JPA model
```

Exemplo:

```text
Order
OrderJpaEntity
```

## Repository

Domain:

```kotlin
interface OrderRepository {
    fun findById(id: OrderId): Order?
    fun save(order: Order): Order
}
```

Infrastructure:

```text
SpringDataOrderRepository
JpaOrderRepository
```

## Regras

- não retornar `JpaEntity` para Application;
- não expor `JpaRepository` fora de Infrastructure;
- não acessar `EntityManager` em Controller;
- não usar entidade JPA como DTO HTTP;
- migrations pertencem ao Flyway;
- produção deve preferir `ddl-auto=validate`.

## Sensitive Data

Evitar usar dados pessoais como chave técnica:

```text
CPF
email
telefone
documento
```

Use um identificador técnico separado.

Dados pessoais devem ser tratados como atributos protegidos, e não como identidade estrutural da aplicação.

A localização detalhada de arquivos e a documentação dos contratos seguem
[Empacotamento e localização](../architecture-ddd/references/packaging.md).
