# REST Guideline

REST pertence à camada `interfaces`.

```text
<context>/interfaces/rest/
```

## Fluxo

```text
HTTP
 ↓
Controller
 ↓
Request DTO
 ↓
Command / Query
 ↓
Use Case
 ↓
Result
 ↓
Response DTO
```

## Controller

Controller deve:

- tratar HTTP;
- validar formato de entrada;
- converter request para command/query;
- executar application use case;
- converter resultado para response.

Controller não deve:

- acessar JPA diretamente;
- acessar Repository diretamente;
- conter regra de negócio;
- publicar Kafka;
- consultar Redis diretamente.

## DTO

DTO HTTP é contrato de interface.

Não reutilizar:

```text
JpaEntity
Domain Entity
Kafka Message
```

como request/response HTTP.

## Validation

`@Valid`, `@NotBlank`, `@Size` e equivalentes validam formato e contrato de entrada.

Regras de negócio continuam no domínio.

## Errors

Usar `@RestControllerAdvice` para mapear erros internos para HTTP.

O domínio não deve conhecer HTTP status codes.

A localização detalhada de arquivos e a documentação dos contratos seguem
[Empacotamento e localização](../architecture-ddd/references/packaging.md).
