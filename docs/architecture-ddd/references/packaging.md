# Empacotamento e localização

Organizar primeiro por bounded context e depois por camada. Dentro da camada,
a pasta explicita o papel; o arquivo identifica um tipo principal. Estes são
os padrões locais do template, sem criar pastas vazias ou novas abstrações.

```text
<context>/
├── domain/
│   ├── model/
│   ├── repository/
│   ├── event/
│   └── exception/
├── application/
│   ├── usecase/
│   │   ├── create/
│   │   │   ├── CreateCustomer.kt
│   │   │   └── CreateCustomerCommand.kt
│   │   ├── find/
│   │   ├── delivery/
│   │   └── retention/
│   ├── port/
│   ├── result/
│   ├── contract/
│   └── exception/
├── infrastructure/
│   ├── persistence/
│   │   ├── entity/
│   │   ├── repository/
│   │   └── adapter/
│   ├── cache/
│   ├── messaging/
│   └── config/
└── interfaces/
    ├── rest/
    │   ├── CustomerController.kt
    │   ├── CustomerExceptionHandler.kt
    │   ├── request/
    │   └── response/
    ├── messaging/
    └── scheduler/
```

## Aplicação

- `usecase/<intenção>/`: caso de uso, commands, queries e resultados exclusivos.
- `port/`: dependências de saída da aplicação; não contém implementações.
- `result/`: resultados compartilhados por vários casos de uso do contexto.
- `contract/`: contratos públicos de integração entre contextos.
- `exception/`: exceções de aplicação, separadas de resultados e contratos.

Agrupar entrega em `delivery` e retenção em `retention`, em vez de colocar
recuperação ou limpeza em `publish`. Cada caso de uso preserva sua fronteira
transacional; mover packages não muda consistência nem comportamento HTTP.

## Persistência

- `entity/`: entidades JPA, como `CustomerJpaEntity`.
- `repository/`: interfaces técnicas Spring Data, como `SpringDataCustomerRepository`.
- `adapter/`: implementações das portas, como `JpaCustomerRepository` e `JdbcCustomerOutbox`.

O repository port continua em `domain/repository/`. Portas de outbox, cache e
idempotência continuam em `application/port/`. Mappers pequenos podem ser
funções privadas do adapter; um mapper independente fica junto ao adapter.
Contextos com apenas JDBC precisam somente de `adapter/`.

## REST e OpenAPI

Controllers e handlers ficam em `interfaces/rest/`; entradas HTTP ficam em
`request/` e saídas em `response/`, sem uma pasta intermediária `dto/`.
Um request ou response não é uma entidade JPA, aggregate ou evento Kafka.

Documentar controllers com tags, operações e respostas reais. Explicar
status de sucesso e erro, autenticação, autorização, headers, parâmetros,
paginação, idempotência e revisão quando aplicáveis. Documentar os campos dos
requests e responses com descrição, exemplos sintéticos, formato e limites.
Respostas sem corpo usam conteúdo vazio; erros usam o contrato HTTP real.
Ocultar o principal JWT dos parâmetros públicos do OpenAPI. Conferir o JSON
gerado em `/v3/api-docs`, além das anotações no código.

## Arquivos

Cada tipo principal público fica em um arquivo homônimo: commands, queries,
requests, responses, portas, exceções, entidades, IDs e value objects.
Evitar arquivos agregadores como `Requests.kt` e `Exceptions.kt`. Tipos
privados/nested exclusivos e funções auxiliares pequenas podem continuar
junto ao dono; não extrair mappers ou interfaces sem necessidade.

## Crescimento e fronteiras

Manter os papéis constantes sem exigir árvores idênticas em todos os contextos.
Quando houver várias capacidades ou aggregates, acrescentar agrupamentos
significativos, como `application/usecase/billing/confirm` e
`infrastructure/persistence/invoice/{entity,repository,adapter}`. Não decidir
bounded contexts pela quantidade de arquivos nem impor limites numéricos.

Cada contexto controla seus jobs de retenção, listeners e configuração dos
consumidores. Políticas e tópicos de um consumidor pertencem ao consumidor;
somente mecanismos técnicos reutilizados ficam em `shared`. Processos que
realmente coordenem contextos precisam de composição explícita.

Começar por módulos lógicos em packages. Testes de arquitetura verificam
fronteiras e ciclos; módulos Gradle só se justificam quando houver necessidade
concreta de isolamento de compilação ou dependências.
