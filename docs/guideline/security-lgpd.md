# Security and LGPD Guideline

Este documento contém requisitos mínimos de engenharia.  
Ele não substitui análise jurídica ou definição formal de base legal.

## Privacy by Design

Proteção de dados deve ser considerada desde a concepção da funcionalidade.

Antes de adicionar um novo dado pessoal, responder:

```text
Por que precisamos dele?
Qual finalidade?
Qual base legal aplicável?
Quem precisa acessar?
Por quanto tempo?
Pode ser eliminado ou anonimizado depois?
```

## Data Minimization

Coletar somente o necessário para a finalidade.

Evitar armazenar dados "para talvez usar no futuro".

## Personal Data

Tratar como pessoal qualquer informação relacionada a pessoa natural identificada ou identificável.

Exemplos comuns:

```text
nome
email pessoal
telefone
CPF
endereço
geolocalização
IP quando vinculável a uma pessoa
identificadores associados a uma pessoa
```

## Sensitive Personal Data

Dados sensíveis exigem proteção reforçada.

Exemplos previstos na LGPD incluem dados relacionados a:

```text
origem racial ou étnica
convicção religiosa
opinião política
filiação sindical
saúde
vida sexual
dados genéticos
dados biométricos vinculados a pessoa natural
```

Não coletar ou persistir dado sensível sem necessidade clara e fundamento adequado.

## Identifiers

Nunca usar CPF, email, telefone ou outro dado pessoal como mecanismo técnico principal de identidade.

Preferir:

```text
internal id → UUIDv7 quando apropriado
personal data → atributos protegidos
```

UUIDv7 não anonimiza dados e não substitui controle de acesso.

## Authorization

Toda operação sobre dados pessoais deve passar por autorização.

Evitar IDOR:

```text
GET /users/{id}
```

não pode autorizar apenas porque `{id}` é difícil de adivinhar.

Validar sempre:

```text
authenticated subject
permission
resource ownership / role
business rule
```

## Access Control

Aplicar least privilege.

- acesso somente para quem precisa;
- separar roles;
- revisar permissões;
- não disponibilizar dados sensíveis em endpoints administrativos genéricos.

## Logs

Por padrão, não registrar:

```text
senhas
tokens
Authorization headers
cookies de sessão
CPF completo
documentos
dados bancários
dados biométricos
dados de saúde
payloads contendo dados sensíveis
```

Quando identificação for necessária para diagnóstico, preferir:

```text
internal ID
correlation ID
masked value
```

## Secrets

Segredos não devem ser armazenados em:

```text
source code
Git
application.yml versionado
logs
Docker image
```

Usar secret manager ou mecanismo equivalente do ambiente.

## Encryption

Dados devem ser protegidos em trânsito.

Usar TLS para comunicação externa e para comunicação interna quando o risco exigir.

Criptografia em repouso deve ser aplicada de acordo com a sensibilidade, ameaça e infraestrutura.

Campos especialmente sensíveis podem exigir proteção adicional em nível de aplicação ou banco.

## Retention

Todo dado pessoal relevante deve possuir uma política de retenção.

Definir:

```text
purpose
retention period
deletion/anonymization rule
exceptions required by law
```

Não manter indefinidamente por padrão.

## Deletion

A exclusão lógica não deve ser confundida automaticamente com eliminação do dado.

Avaliar também:

```text
primary database
replicas
cache
search indexes
object storage
logs
analytics
backups
```

Backups podem seguir política própria documentada de expiração.

## Redis

Evitar persistir dados pessoais/sensíveis em Redis sem necessidade.

Quando necessário:

- TTL obrigatório quando compatível com o caso;
- acesso restrito;
- não expor Redis publicamente;
- evitar payloads completos se apenas um identificador for suficiente.

## Kafka

Mensagens Kafka devem carregar apenas os dados necessários ao consumidor.

Preferir:

```text
entityId
eventId
eventType
minimal business payload
```

Evitar replicar perfis pessoais completos em eventos.

Definir retenção dos tópicos de acordo com a natureza dos dados.

## External APIs

Antes de enviar dados pessoais para terceiros:

- confirmar necessidade;
- limitar payload;
- documentar integração;
- definir responsabilidades;
- avaliar transferência internacional quando aplicável;
- não enviar dados adicionais por conveniência.

## Data Subject Requests

A arquitetura deve permitir localizar os dados associados a uma pessoa.

Evitar espalhar cópias sem rastreabilidade entre:

```text
PostgreSQL
Redis
Kafka
object storage
analytics
third parties
```

## Security Incidents

Falhas envolvendo dados pessoais devem possuir fluxo de resposta.

No mínimo:

```text
detect
contain
record
assess impact
notify responsible people
preserve evidence
correct root cause
```

A obrigação de comunicação externa depende do incidente e das regras aplicáveis.

## Basic Review Checklist

Antes de aprovar uma feature com dados pessoais:

- [ ] finalidade documentada;
- [ ] somente dados necessários;
- [ ] base legal identificada por responsável apropriado;
- [ ] autorização implementada;
- [ ] dados não aparecem indevidamente em logs;
- [ ] retenção definida;
- [ ] cache avaliado;
- [ ] eventos Kafka minimizados;
- [ ] integrações externas avaliadas;
- [ ] exclusão/anonimização considerada;
- [ ] testes não usam dados pessoais reais;
- [ ] dados sensíveis recebem proteção proporcional ao risco.

## Referências oficiais

Consultar sempre a versão vigente da LGPD e os materiais da Autoridade Nacional de Proteção de Dados (ANPD),
especialmente:

- materiais educativos e publicações da ANPD;
- guia de segurança da informação;
- orientações sobre direitos dos titulares;
- orientações sobre hipóteses legais;
- regulamentações aplicáveis ao tipo de agente de tratamento.
