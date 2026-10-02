# Casos de uso, transações e consistência

Organizar cada intenção em `<base-package>.<context>.application.usecase.<use-case>`.
Manter o caso de uso e seus command/query/result em arquivos homônimos próximos;
resultados compartilhados ficam em `application/result/`:

```text
application/usecase/confirm/
├── ConfirmOrder.kt
├── ConfirmOrderCommand.kt
└── ConfirmOrderResult.kt
```

Nomes como `ConfirmOrder`, `CancelOrder` e `ApprovePayment` expressam a intenção.
Evitar serviços genéricos que concentrem operações sem propósito claro.

## Fluxo e responsabilidades

O caso de uso valida condições de aplicação, carrega aggregates, chama seu
comportamento, persiste alterações, coordena portas externas e retorna um
resultado. As invariantes e decisões de negócio pertencem ao domínio.

Depender de repository ports do domínio e de portas de saída em
`application/port/`, como `PaymentGateway` ou `OrderEventPublisher`.
Não importar controllers, entidades JPA ou templates Kafka/Redis.

Commands alteram estado; queries consultam estado. Usar CQRS leve, sem exigir
infraestrutura distribuída. Queries complexas podem usar projeções otimizadas
por uma porta de leitura, sem reconstruir um aggregate inteiro ou expor JPA.

## Transações

Preferir a fronteira transacional no caso de uso, com `@Transactional` quando
adequado ao mecanismo de persistência. Evitar transações em controllers.
Avaliar quais operações precisam ser atômicas e como chamadas externas
interagem com duração da transação e falhas. Escolher o mecanismo transacional
compatível com a tecnologia utilizada; não presumir que uma transação JPA
abrange um pipeline reativo arbitrário.

## Eventos críticos

Persistir dados e publicar no broker são operações distintas. Quando perda ou
inconsistência de eventos afetar o negócio, considerar Transactional Outbox:

```text
Transação do banco: dados de negócio + registro outbox
Commit
Publicador da outbox → broker
```

Não tratar uma chamada ao publisher após `save()` como garantia de entrega
atômica. Projetar recuperação e idempotência conforme o impacto das falhas.
Outbox é uma decisão de consistência, não uma exigência para todo evento.
