# Persistência

Stack:

- Spring Data JPA
- Hibernate
- PostgreSQL
- Flyway

## Separação de modelos

```text
Order             → Domain
OrderJpaEntity    → Persistence
OrderResponse     → HTTP
```

JPA Entities não devem ser automaticamente tratadas como Domain Entities.

## Repository

```text
domain/
└── repository/OrderRepository.kt

infrastructure/persistence/
├── entity/OrderJpaEntity.kt
├── repository/SpringDataOrderRepository.kt
└── adapter/JpaOrderRepository.kt
```

## Flyway

Flyway controla o schema.

Em produção:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
```

Hibernate valida. Flyway cria e migra.

## IDs

Preferir IDs gerados pela aplicação, como UUIDv7, quando adequado.

Representar IDs importantes por tipos próprios:

```kotlin
@JvmInline
value class OrderId(val value: UUID)
```
