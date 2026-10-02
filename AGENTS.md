# Contexto da aplicação

Para implementar ou revisar Spring/Kotlin, usar a skill local
[spring-kotlin](.agents/skills/spring-kotlin/SKILL.md) e carregar suas referências conforme
a tarefa. Ela reúne as convenções; este arquivo registra apenas o contexto local.

- Package raiz: `com.kotlin.template`; entrada: `TemplateApplication.kt`.
- Usar `./gradlew`. O build exige toolchain Java 25; o Java do terminal pode ser outro.
- HTTP usa WebFlux/Netty e persistência usa R2DBC PostgreSQL. Casos de uso e
  portas em `application/port/` retornam `Mono`/`Flux`; o domínio permanece
  independente de Reactor. Manter transações reativas nos casos de uso, sem
  `block()` ou `subscribe()` manual no código de produção.
- Redis usa `ReactiveStringRedisTemplate`. Kafka usa listeners que retornam
  `Mono<Void>` e `max.poll.records=1` para preservar a ordenação; adaptar futures
  do produtor com Reactor e isolar a chamada de envio em `boundedElastic`.
- Flyway usa JDBC exclusivamente na inicialização. `DATABASE_URL` é R2DBC;
  `FLYWAY_DATABASE_URL` é JDBC e deve apontar para o mesmo banco. As referências
  JPA/MVC da skill são genéricas; neste projeto prevalece esta stack reativa.
- `compose.yaml` contém PostgreSQL, Redis e Kafka (KRaft em um único nó).
  Kafka anuncia `localhost:9092` para o host e `kafka:19092` na rede Docker.
  Testes de integração usam serviços independentes via Testcontainers.
- Para validar mudanças, selecionar testes relevantes com `./gradlew test --tests '<classe>'`;
  testes de integração existentes usam Docker/Testcontainers.
