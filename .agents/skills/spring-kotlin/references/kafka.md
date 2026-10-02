# Kafka e eventos

Kafka é um mecanismo de integração, isolado do domínio por adapters e portas.

## Entrada

Colocar listeners em `<base-package>.<context>.interfaces.messaging`.
Um `OrderKafkaListener` desserializa/valida o contrato, converte a mensagem
em command e delega a um caso de uso. Não colocar regra de negócio no listener.
Manter a transformação mensagem/command nessa fronteira.

## Saída

Definir a porta de publicação em `application/port/`, como
`OrderEventPublisher`. Implementar com `KafkaOrderEventPublisher` em
`infrastructure/messaging/`. A aplicação depende da porta; somente o adapter
conhece `KafkaTemplate`, tópicos e serialização.

```text
Aggregate → Domain event → Application → Publisher port → Kafka adapter
```

## Contratos

Um domain event representa um fato interno, como `OrderConfirmed`. Um
integration event define contrato externo, como `order.confirmed.v1`. Uma
mensagem Kafka representa o transporte. Separar esses modelos quando seus
ciclos de evolução forem diferentes; não reutilizar um DTO HTTP por conveniência.

Mensagens devem conter apenas dados necessários: IDs técnicos, event ID,
tipo e payload mínimo de negócio. Evitar copiar perfis pessoais completos.
Definir retenção dos tópicos conforme a natureza e finalidade dos dados.

## Consistência

Para eventos críticos, avaliar Transactional Outbox: gravar dados e registro
de evento na mesma transação do banco; publicar a outbox após commit.
Um commit PostgreSQL seguido de publish Kafka pode deixar operações
inconsistentes. Escolher a estratégia conforme o impacto no negócio.

Tratar possibilidade de reentrega e definir idempotência e recuperação de
falhas conforme o fluxo. Não presumir entrega exatamente uma vez de ponta a
ponta. Verificar contratos, serialização e fluxos de integração relevantes
quando a mudança afetar o comportamento dessas fronteiras.

O contexto consumidor controla sua factory, DLT e configuração. Compartilhar
apenas o mecanismo técnico comum. Exceções do listener ficam na sua interface
de mensageria, fora dos contratos públicos do produtor.
