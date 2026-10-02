# Contexto da aplicação

Para implementar ou revisar Spring/Kotlin, usar a skill local
[spring-kotlin](skills/spring-kotlin/SKILL.md) e carregar suas referências conforme
a tarefa. Ela reúne as convenções; este arquivo registra apenas o contexto local.

- Package raiz: `com.kotlin.template`; entrada: `TemplateApplication.kt`.
- Usar `./gradlew`. O build exige toolchain Java 25; o Java do terminal pode ser outro.
- HTTP usa Spring MVC e persistência usa JPA bloqueante. Manter a fronteira
  transacional nos casos de uso e Open Session in View desabilitado.
- `compose.yaml` contém PostgreSQL, Redis e Kafka (KRaft em um único nó).
  Kafka anuncia `localhost:9092` para o host e `kafka:19092` na rede Docker.
  Testes de integração usam serviços independentes via Testcontainers.
- Para validar mudanças, selecionar testes relevantes com `./gradlew test --tests '<classe>'`;
  testes de integração existentes usam Docker/Testcontainers.
