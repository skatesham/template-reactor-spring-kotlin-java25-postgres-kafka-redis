# Kotlin and Java

Kotlin e Java podem coexistir no mesmo bounded context.

A arquitetura não muda conforme a linguagem.

## Source roots

```text
src/main/kotlin/
src/main/java/
```

Ambos devem usar o mesmo package lógico:

```text
<base-package>.<context>....
```

## Regras

- não separar `java/` e `kotlin/` por responsabilidade arquitetural;
- evitar duplicar abstrações apenas por diferença de linguagem;
- interoperabilidade deve ser tratada na API da classe;
- evitar nullability ambígua nas fronteiras Java/Kotlin.

## Kotlin

Preferir tipos explícitos para conceitos de domínio:

```kotlin
@JvmInline
value class OrderId(val value: UUID)
```

Usar `data class` principalmente para valores, commands, queries e DTOs imutáveis.

Não assumir que toda Entity de domínio deve ser `data class`.

## Java

Java pode implementar qualquer camada.

Não criar uma área `legacy` apenas porque uma classe está em Java.

O package deve continuar representando o domínio e a responsabilidade arquitetural.
