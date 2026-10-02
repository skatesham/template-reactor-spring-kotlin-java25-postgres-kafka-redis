# Development Guideline

Esta pasta complementa a documentação de arquitetura.

Ela é propositalmente **prescritiva** e **genérica**.  
Use `<base-package>` como placeholder para o package raiz real do projeto.

Exemplo:

```text
<base-package> = com.example.application
```

A documentação de arquitetura explica **por que** as decisões existem.  
Esta guideline explica **onde colocar e como implementar** cada elemento.

## Índice

- [Class Placement](class-placement.md)
- [Dependency Rules](dependency-rules.md)
- [Naming Conventions](naming-conventions.md)
- [Use Cases](use-cases.md)
- [REST](rest.md)
- [Persistence](persistence.md)
- [Kafka](kafka.md)
- [Redis](redis.md)
- [UUID and Identifiers](identifiers.md)
- [Security and LGPD](security-lgpd.md)
- [Kotlin and Java](kotlin-java.md)
- [Testing](testing.md)

## Estrutura de referência

```text
<base-package>/
├── shared/
└── <context>/
    ├── domain/
    ├── application/
    ├── infrastructure/
    └── interfaces/
```

A pasta `shared` deve permanecer pequena.

Novas funcionalidades devem preferencialmente ser adicionadas dentro do bounded context ao qual pertencem.
