# Redis e cache

Colocar adapters em `<base-package>.<context>.infrastructure.cache`.
Redis pode atender cache, rate limiting, idempotência, counters, sessões e
estado distribuído temporário; locks somente quando houver necessidade real.

## Fronteira e fonte de verdade

Quando o cache participar explicitamente do fluxo de aplicação, definir uma
porta em `application/port/`, como `OrderCache`, e implementar com
`RedisOrderCache`. Controllers e domínio não usam `RedisTemplate`.

```text
Application → Cache
                ├── hit → resultado
                └── miss → fonte de verdade → atualização do cache
```

O cache não contém regras de negócio. Não substituir PostgreSQL como fonte
de verdade sem decisão arquitetural explícita. Cache miss é comportamento
esperado; para cache derivado, definir fallback à fonte de verdade.
Para idempotência, sessão ou rate limiting, definir a reação à indisponibilidade
conforme a função e o risco, sem reutilizar automaticamente o fallback de cache.

## Ciclo de vida

- Definir TTL explicitamente para entradas de cache e estado temporário.
- Invalidar ou atualizar o cache após mudança confirmada da fonte de verdade.
- Avaliar a consistência entre commit, atualização e leitores concorrentes.
- Não presumir que uma chave sempre existe ou que Redis nunca falha.

## Dados e acesso

Guardar somente dados necessários; preferir um ID a payloads completos.
Evitar dados pessoais/sensíveis sem necessidade concreta. Quando necessários,
aplicar retenção/TTL compatível com a finalidade e acesso restrito.

Não expor Redis publicamente nem armazenar segredos em texto puro sob a
justificativa de rede privada. Incluir entradas e cópias Redis no fluxo de
eliminação de dados pessoais.

Validar integrações relevantes com Redis real em container quando a mudança
depender de TTL, serialização, invalidação ou comportamento do servidor.
