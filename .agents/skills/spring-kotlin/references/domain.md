# Domínio, patterns e identificadores

O domínio representa o negócio sem dependências de Spring, JPA, Kafka, Redis
ou HTTP. Colocar aggregates, entities e value objects em
`<base-package>.<context>.domain.model`.

## Invariantes e tipos

O aggregate root protege as invariantes e controla suas mudanças. Expor
comportamentos como `order.confirm()`; evitar setters que permitam estados
inválidos. Não mover uma regra do aggregate para o caso de uso apenas para
simplificar a entidade.

Representar conceitos relevantes por tipos próprios, como `Money`, `Email`,
`Percentage` e `OrderId`. Validar value objects na criação quando necessário.
Usar `data class` para valores imutáveis; entidades com identidade e ciclo de
vida não precisam ser `data class`.

```kotlin
@JvmInline
value class OrderId(val value: UUID)
```

O exemplo representa um UUID já recebido; não implementa sua geração.

## Patterns conforme a necessidade

| Pattern        | Quando usar                                                                                                                     |
|----------------|---------------------------------------------------------------------------------------------------------------------------------|
| Repository     | Expressar operações de persistência na linguagem do domínio; interface em `domain/repository/`, implementação na infraestrutura |
| Factory        | Construção do aggregate envolve regras próprias; colocar em `domain/factory/`                                                   |
| Strategy       | Existem comportamentos intercambiáveis reais                                                                                    |
| Specification  | Regras precisam ser combinadas e reutilizadas; colocar em `domain/specification/`                                               |
| Domain service | Regra de negócio não pertence naturalmente a uma única entity/value object; colocar em `domain/service/`                        |
| Domain event   | Registrar um fato de negócio como `OrderConfirmed`; colocar em `domain/event/`                                                  |

Eventos de domínio não conhecem tópico, serializador ou broker. Não criar
hierarquias genéricas que ocultem o vocabulário do negócio.

## Identificadores

Preferir IDs técnicos independentes de dados pessoais. Não usar CPF, email,
telefone, biometria ou informação de saúde como chave técnica principal.
Esses dados, quando necessários, são atributos protegidos.

UUIDv7 é uma opção para IDs internos, primary keys e event IDs quando banco e
ecossistema o suportarem. Sua ordenação temporal aproximada favorece a
localidade de índices. Escolher um gerador apropriado já disponível ou avaliar
uma dependência necessária; não confundir `UUID.randomUUID()` com UUIDv7.

UUID não autoriza acesso, não é segredo e não anonimiza uma pessoa. UUIDv7
expõe informação temporal aproximada; avaliar essa exposição em IDs públicos
e correlation IDs. Para links secretos, reset de senha e capabilities, usar
token criptograficamente aleatório específico. Recursos sensíveis podem exigir
um identificador público opaco separado do ID interno.
