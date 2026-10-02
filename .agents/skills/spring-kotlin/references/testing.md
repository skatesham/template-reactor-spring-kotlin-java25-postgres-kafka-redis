# Testes pelas fronteiras da aplicação

Escolher verificações pelo comportamento alterado e pelo pedido do usuário.
Não exigir a inicialização de toda a aplicação para uma regra de domínio nem
adicionar testes que apenas repetem a implementação.

| Fronteira      | Estratégia                                                                        |
|----------------|-----------------------------------------------------------------------------------|
| Domínio        | Unit tests sem Spring e sem banco; preferencialmente sem mocks                    |
| Aplicação      | Casos de uso com ports substituídos por fakes/mocks quando adequado               |
| JPA/PostgreSQL | Testcontainers com PostgreSQL real para SQL, constraints e mapeamentos relevantes |
| Redis          | Container real para comportamento de TTL, serialização e cache relevantes         |
| Kafka          | Serialização, contratos e fluxos importantes de integração                        |
| REST           | Status, validação, contrato, autorização e mapeamento de erros                    |

## Infraestrutura e execução

Usar o Gradle Wrapper e as ferramentas de teste existentes na aplicação.
Inspecionar configurações de Testcontainers e service connections antes de
duplicar configuração. Um container disponível em testes não implica que o
mesmo serviço esteja configurado no ambiente de desenvolvimento.

Não substituir PostgreSQL automaticamente por H2 quando o comportamento do
banco fizer parte do que está sendo verificado. Escolher MVC ou WebFlux e
o cliente de teste conforme o stack efetivamente utilizado.

Preferir o teste específico da mudança. Quando uma execução depender de
Docker, serviços ou toolchain indisponíveis, informar a limitação e não
descrever o resultado como validado.

## Dados e cenários

Usar fixtures sintéticas. Não copiar dumps de produção ou dados pessoais reais
de clientes. Se dados reais forem inevitáveis por necessidade legítima, exigir
processo específico de anonimização/pseudonimização e controle de acesso.

Incluir falhas e limites relevantes: violação de invariantes, entrada inválida,
recurso não autorizado, cache miss e falhas de integração quando afetados.
Verificar o comportamento observável, sem acoplar o teste à organização interna
das classes.
