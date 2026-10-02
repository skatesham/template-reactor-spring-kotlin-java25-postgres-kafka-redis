# Identifiers

## Default

Preferir identificadores técnicos independentes dos dados de negócio.

Quando o ecossistema e banco suportarem adequadamente, UUIDv7 é uma boa opção para IDs internos por combinar unicidade
distribuída com ordenação temporal.

Exemplo conceitual:

```kotlin
@JvmInline
value class CustomerId(
    val value: UUID
)
```

## UUIDv7

UUIDv7 pode ser utilizado para:

- primary keys;
- aggregate IDs;
- event IDs;
- correlation IDs quando a característica temporal for aceitável.

Benefícios típicos:

- geração distribuída;
- ordenação aproximada por tempo;
- melhor localidade de índice que UUIDv4 aleatório.

## Segurança

UUID não é mecanismo de autorização.

Nunca assumir:

```text
"se o usuário não conhece o UUID, ele não consegue acessar"
```

Todo acesso deve ser autorizado independentemente do ID.

## Dados pessoais e sensíveis

Não usar diretamente como identificador técnico:

```text
CPF
RG
email
telefone
biometria
dados de saúde
```

Use um ID técnico e mantenha o dado pessoal como atributo protegido.

### Atenção ao UUIDv7

UUIDv7 contém informação temporal aproximada de geração.

Portanto:

- não tratá-lo como segredo;
- não tratá-lo como anonimização;
- avaliar exposição pública quando o horário de criação puder revelar informação;
- para links secretos, reset de senha, confirmação ou capabilities, usar token criptograficamente aleatório específico
  para esse propósito;
- para recursos especialmente sensíveis, considerar um identificador público opaco separado do ID interno.

Um UUID associado a uma pessoa continua podendo ser dado pessoal se permitir associação ou reidentificação.
