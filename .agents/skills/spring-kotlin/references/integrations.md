# Integrações externas

O caso de uso depende de uma porta em
`<base-package>.<context>.application.port`, como `PaymentGateway`.
A implementação técnica, como `HttpPaymentGateway`, pertence a
`infrastructure/client/`. Não acoplar a aplicação ao SDK ou cliente do fornecedor.

## Tradução de modelos

```text
External API → External DTO → Mapper/ACL → Modelo interno
```

Manter DTOs e mappers externos próximos do adapter. Traduzir conceitos, erros
e estados do fornecedor para o contrato interno. Objetos externos não entram
diretamente no domínio; essa tradução protege a linguagem e as invariantes
do bounded context.

Integrações entre contextos internos usam API de aplicação/facade, porta ou
evento. Não acessar diretamente repositórios internos de outro contexto.

## Falhas e efeitos externos

Definir timeouts, tratamento de falhas e necessidade de idempotência conforme
a operação. Repetir uma chamada com efeito externo exige considerar se o
efeito já ocorreu; não aplicar retries indiscriminadamente.

Uma transação de banco não desfaz automaticamente uma operação do fornecedor.
Considerar duração de chamadas externas e inconsistência possível ao coordenar
persistência e efeitos de integração.

## Dados pessoais

Antes de enviar dados a terceiros, confirmar necessidade, limitar payload,
documentar a integração e as responsabilidades. Encaminhar a avaliação de
fundamento e transferência internacional ao responsável apropriado quando
aplicável; não inventar uma base legal.

Guardar segredos no mecanismo do ambiente, sem incluí-los em código, imagens
ou logs. Proteger o transporte e evitar registrar credenciais ou payloads
sensíveis. Considerar cópias em terceiros nos processos de retenção e eliminação.
