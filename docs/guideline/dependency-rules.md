# Dependency Rules

## Permitido

```text
interfaces       → application
application      → domain
infrastructure   → application
infrastructure   → domain
domain           → domain
```

## Proibido

```text
domain → Spring
domain → Spring/Reactor/R2DBC
domain → Kafka
domain → Redis
domain → REST

application → Controller
application → SQL Row
application → KafkaTemplate
application → RedisTemplate

Controller → Repository
Controller → EntityManager

<context-a> → repository interno de <context-b>
```

## Cross-context

A comunicação entre bounded contexts deve ocorrer por:

```text
Application API / Facade
Port
Event
```

Nunca importar diretamente detalhes internos de persistência de outro contexto.

## Framework annotations

Annotations técnicas devem permanecer nas camadas externas sempre que possível.

Aceitável:

```text
@Service
@Transactional
@Repository
@RestController
@Entity
@KafkaListener
```

nas respectivas camadas técnicas.

Evitar essas annotations dentro de objetos de domínio.
