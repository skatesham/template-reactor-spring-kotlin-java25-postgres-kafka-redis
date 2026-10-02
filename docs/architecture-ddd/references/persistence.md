# Persistência reativa

PostgreSQL usa Spring Data R2DBC e `DatabaseClient`. Adapters ficam em
`<context>/infrastructure/persistence/adapter/`, como `R2dbcCustomerRepository`.
As portas de persistência retornam `Mono`/`Flux` e ficam em `application/port/`;
aggregates, IDs e invariantes de domínio não importam Reactor ou Spring.

Adapters traduzem rows SQL em modelos de domínio ou projeções de aplicação.
Controllers acessam somente casos de uso. Não expor `DatabaseClient`, `Row` ou
modelos de persistência nos contratos HTTP ou nas portas de aplicação.
Não criar pastas de entidades ou repositories Spring Data quando não utilizadas.

`@Transactional` nos casos de uso usa `R2dbcTransactionManager`. Compor comandos
sequencialmente com `flatMap`, `then` e `concatMap`; não executar SQL concorrente
na mesma conexão transacional. SQL é executado quando o publisher é subscrito.
Não usar `block()` nem subscriptions manuais em produção. Locks `FOR UPDATE`,
`FOR SHARE` e `SKIP LOCKED` permanecem ativos até commit ou rollback reativo.

Flyway controla o schema nas migrations versionadas em `db/migration`.
A URL `DATABASE_URL` usa `r2dbc:postgresql://`; `FLYWAY_DATABASE_URL` usa
`jdbc:postgresql://` e deve apontar para o mesmo banco. JDBC é usado apenas
na inicialização pelas migrations; não há ORM, validação Hibernate nem OSIV.

IDs técnicos são independentes de dados pessoais; UUID não substitui autorização.
Verificar constraints, rollback e concorrência em PostgreSQL real via Testcontainers.
