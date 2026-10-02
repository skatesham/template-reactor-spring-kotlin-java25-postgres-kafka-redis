# Testing Guideline

## Domain

```text
unit test
sem Spring
sem banco
```

## Application

Testar use cases com ports substituídos por fakes/mocks quando adequado.

## Persistence

Usar PostgreSQL real em Testcontainers para comportamento dependente do banco.

Evitar H2 como substituto automático de PostgreSQL quando SQL, constraints ou comportamento do banco forem relevantes.

## Redis

Integrações importantes devem ser validadas contra Redis real via container.

## Kafka

Testar serialização, contratos e fluxos relevantes de integração.

## REST

Testar:

- status;
- validação;
- contrato;
- authorization;
- error mapping.

## Personal Data

Nunca usar dados pessoais reais de clientes/usuários em fixtures.

Gerar dados sintéticos.

Evitar copiar dumps de produção para desenvolvimento/teste.

Quando um dataset real for inevitável por motivo legítimo, exigir processo específico de anonimização/pseudonimização e
controle de acesso.
