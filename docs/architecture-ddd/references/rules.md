# Regras Arquiteturais

## Dependências proibidas

```text
domain → infrastructure
domain → interfaces
domain → Spring

application → Controller
application → SQL Row
application → KafkaTemplate

controller → repository

context A → repository interno de context B
```

## Dependências permitidas

```text
interfaces → application
application → domain
infrastructure → application
infrastructure → domain
domain → domain
```

## Outras regras

- Constructor Injection.
- Evitar Field Injection.
- Evitar `GenericService<T>`.
- Evitar `GenericRepository<T>` quando esconder linguagem de domínio.
- Evitar packages genéricos como `utils` e `helpers`.
- `shared` deve ser pequeno.
- Preferir nomes da Ubiquitous Language.
- Não criar abstrações antes de existir necessidade real.
- Não transformar bounded contexts automaticamente em microservices.
