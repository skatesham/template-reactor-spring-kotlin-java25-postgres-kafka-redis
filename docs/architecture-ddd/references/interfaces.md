# Interfaces

## REST

Controllers são adapters de entrada.

```text
HTTP
 ↓
Controller
 ↓
Command / Query
 ↓
Application
 ↓
Domain
```

Controller deve:

- validar formato;
- converter Request DTO;
- executar caso de uso;
- converter resposta.

Controller não deve:

- acessar Repository diretamente;
- conter regra de negócio;
- executar SQL;
- publicar Kafka diretamente.

## DTOs

Cada fronteira deve possuir seu modelo quando necessário.

```text
CreateOrderRequest
    ↓
CreateOrderCommand
    ↓
Order
    ↓
OrderResult
    ↓
OrderResponse
```

Evitar reutilizar a mesma classe para REST, banco, Kafka e domínio.

## Localização e documentação

Requests ficam em `interfaces/rest/request/`, responses em
`interfaces/rest/response/`, cada tipo público em arquivo homônimo.
Controllers e handlers ficam na raiz de `interfaces/rest/`. Não criar `dto/`.

OpenAPI deve explicar operações, autenticação e permissões, os parâmetros e
headers, schemas de entrada/saída e status de sucesso e erro. Registrar as
semânticas de idempotência, paginação e concorrência quando existirem. Ocultar
o principal autenticado e usar exemplos sintéticos. Não inventar bodies para
respostas 202/204 que não os retornam. Validar o contrato gerado em `/v3/api-docs`.

Listeners, configuração de consumo e jobs pertencem ao contexto responsável.
Erros de transporte do listener ficam junto à interface de mensageria; não
expor exceções internas do produtor como contratos de integração.
