# Class Placement

Este arquivo define onde cada tipo de classe deve ficar.

## Domain

```text
<context>/domain/
```

| Tipo             | Local                   |
|------------------|-------------------------|
| Aggregate Root   | `domain/model/`         |
| Entity           | `domain/model/`         |
| Value Object     | `domain/model/`         |
| Domain Event     | `domain/event/`         |
| Domain Exception | `domain/exception/`     |
| Domain Service   | `domain/service/`       |
| Specification    | `domain/specification/` |
| Repository Port  | `application/port/`    |
| Factory          | `domain/factory/`       |

## Application

```text
<context>/application/
```

Preferir organização por caso de uso:

```text
application/
├── usecase/create/
├── usecase/update/
├── usecase/delete/
└── usecase/find/
```

| Tipo                  | Local                                           |
|-----------------------|-------------------------------------------------|
| Use Case              | `application/usecase/<feature>/`                        |
| Command               | `application/usecase/<feature>/`                        |
| Query                 | `application/usecase/<feature>/`                        |
| Result                | `application/usecase/<feature>/`                        |
| Outbound Port         | `application/port/`                             |
| Application Exception | `application/exception/` |

## Interfaces

```text
<context>/interfaces/
```

| Tipo                   | Local                   |
|------------------------|-------------------------|
| REST Controller        | `interfaces/rest/`      |
| Request DTO            | `interfaces/rest/request/`      |
| Response DTO           | `interfaces/rest/response/`      |
| REST Exception Handler | `interfaces/rest/`      |
| Kafka Listener         | `interfaces/messaging/` |
| Scheduled input job    | `interfaces/scheduler/` |

## Infrastructure

```text
<context>/infrastructure/
```

| Tipo                   | Local                                                             |
|------------------------|-------------------------------------------------------------------|
| Modelo de persistência (opcional)             | `infrastructure/persistence/entity/`                                     |
| Spring Data Repository | `infrastructure/persistence/repository/`                                     |
| Repository Adapter     | `infrastructure/persistence/adapter/`                                     |
| Persistence Mapper     | `infrastructure/persistence/adapter/`                                     |
| Redis Adapter          | `infrastructure/cache/`                                           |
| Kafka Publisher        | `infrastructure/messaging/`                                       |
| External HTTP Client   | `infrastructure/client/`                                          |
| Spring Configuration   | próximo da infraestrutura configurada ou `infrastructure/config/` |

## Mapper

O mapper deve ficar próximo da fronteira que transforma.

```text
REST ↔ Application
→ interfaces/rest/

Domain ↔ SQL row
→ infrastructure/persistence/

External API ↔ Internal model
→ infrastructure/client/

Kafka message ↔ Application command
→ interfaces/messaging/
```

Evitar um package global:

```text
mapper/
```

com transformações de toda a aplicação.

Resultados compartilhados ficam em `application/result/`; contratos públicos de
integração em `application/contract/`. Cada tipo principal público fica em um
arquivo homônimo. Ver [padrão completo](../architecture-ddd/references/packaging.md).
