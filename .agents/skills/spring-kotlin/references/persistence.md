# JPA, PostgreSQL e Flyway

Colocar persistência em
`<base-package>.<context>.infrastructure.persistence`, com os papéis abaixo.

| Papel                         | Exemplo                     |
|-------------------------------|-----------------------------|
| Modelo de domínio             | `Order`                     |
| Porta em `domain/repository/` | `OrderRepository`           |
| Modelo JPA em `entity/`       | `OrderJpaEntity`            |
| Spring Data em `repository/`  | `SpringDataOrderRepository` |
| Adapter em `adapter/`         | `JpaOrderRepository`        |

## Fronteira

O domínio define operações com nomes e tipos de negócio. A infraestrutura
implementa a porta e traduz entre domínio e JPA com mapper local.

Não retornar entidades JPA à aplicação, expor `JpaRepository` fora da
infraestrutura ou usar entidade JPA como DTO HTTP. Controllers não acessam
`EntityManager` nem executam SQL. Queries otimizadas retornam projeções próprias
pela API de aplicação, mantendo detalhes técnicos internos ao adapter.

Ao mapear aggregates, preservar invariantes, identidade e mudanças de estado.
Não depender de um objeto de domínio ser uma entidade Hibernate gerenciada.
Conferir abertura de classes e construção de entidades com os plugins Kotlin
já configurados no build.

## Schema e transação

Flyway controla criação e evolução do schema. Colocar migrations nos locais
configurados pela aplicação; usar `src/main/resources/db/migration/` quando
ela utiliza a configuração padrão. Em produção, preferir
`spring.jpa.hibernate.ddl-auto=validate`.

Coordenar alterações atômicas na fronteira do caso de uso, evitando transações
em controllers. JPA é bloqueante: sua transação e suas chamadas precisam de
uma fronteira explícita quando a entrada da aplicação for reativa.

## Identidade e dados

Usar IDs técnicos separados de CPF, email ou telefone. UUIDv7 pode ser gerado
pela aplicação quando adequado e suportado. Não considerar UUID um controle
de acesso. Dados pessoais são atributos protegidos, sujeitos à finalidade,
retenção e eliminação definidas para a funcionalidade.

Verificar comportamento específico de SQL, constraints e mapeamento com
PostgreSQL real via Testcontainers quando a alteração depender desses aspectos.
Não substituir PostgreSQL automaticamente por H2.

Adapters JDBC também ficam em `adapter/`. Cada tipo principal fica em arquivo
homônimo. Mappers pequenos podem ser privados no adapter; mappers independentes
ficam próximos dele. Não criar `entity/` e `repository/` em contextos somente JDBC.
