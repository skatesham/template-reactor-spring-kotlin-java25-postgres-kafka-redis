# Segurança e privacidade

Estas são orientações de engenharia para funcionalidades que lidam com
autorização, segredos ou dados pessoais. Não determinam uma base legal nem
substituem análise jurídica. Quando a tarefa depender de obrigação legal,
verificar as regras vigentes e encaminhar decisões ao responsável apropriado.

## Finalidade e minimização

Antes de adicionar um dado pessoal, identificar finalidade, necessidade,
responsável pela base legal, quem acessa, período de retenção e possibilidade
de eliminação/anonimização. Coletar somente o necessário.

Informações que permitem identificar ou associar uma pessoa, incluindo IDs,
IPs e geolocalização, podem exigir proteção. Saúde, biometria, genética e
outros dados sensíveis exigem proteção proporcional ao risco e fundamento
adequado. Não adicioná-los por conveniência.

## Identidade e autorização

Usar IDs técnicos separados de CPF, email e telefone. UUID não é segredo,
anonimização ou autorização; UUIDv7 também revela tempo aproximado de geração.
Para links secretos e reset de senha, usar tokens criptograficamente aleatórios
específicos.

Toda operação verifica sujeito autenticado, permissão e vínculo com o recurso,
conforme a regra de negócio. Evitar IDOR e aplicar menor privilégio; endpoints
administrativos não devem expor dados sensíveis de forma genérica.

## Logs, segredos e proteção

- Não registrar senhas, tokens, headers `Authorization`, cookies, documentos
  completos, dados bancários, biométricos, de saúde ou payloads sensíveis.
- Para diagnóstico, preferir IDs internos, correlation IDs ou valores mascarados;
  eles também podem exigir controle de acesso e retenção.
- Não armazenar segredos em código, arquivos de configuração versionados,
  imagens Docker ou logs. Usar secret manager ou mecanismo do ambiente.
- Proteger o transporte com TLS; avaliar comunicação interna e criptografia
  em repouso conforme sensibilidade, ameaça e infraestrutura. Campos sensíveis
  podem exigir proteção adicional.

## Retenção e eliminação

Definir finalidade, prazo, regra de eliminação/anonimização e exceções aplicáveis.
Soft delete não equivale automaticamente a eliminar o dado.
Considerar banco, réplicas, caches, índices, objetos, logs, analytics, eventos,
backups e terceiros. Backups podem seguir política documentada de expiração.
Manter rastreabilidade das cópias para atender solicitações de titulares.

Redis guarda o mínimo necessário, com TTL quando compatível e acesso restrito.
Eventos Kafka carregam payload mínimo e têm retenção definida para seus dados.
Integrações externas enviam somente o necessário e têm responsabilidades
documentadas, incluindo avaliação de transferência internacional quando cabível.

## Verificação e incidentes

Em uma mudança com dados pessoais, verificar autorização, exposição em
respostas/logs, minimização, retenção e eliminação nas cópias afetadas.
Usar dados sintéticos em testes; datasets reais exigem processo específico
de anonimização/pseudonimização e controle de acesso.

Incidentes seguem detecção, contenção, registro, avaliação de impacto,
acionamento dos responsáveis, preservação de evidência e correção da causa.
Comunicação externa depende da avaliação do incidente e das regras aplicáveis.
