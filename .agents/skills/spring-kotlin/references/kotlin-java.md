# Kotlin, Java e composição Spring

Kotlin e Java podem implementar qualquer camada do mesmo bounded context.
Usar `src/main/kotlin/` e `src/main/java/` com o mesmo package lógico
`<base-package>.<context>`. Não separar responsabilidades por linguagem nem
criar uma área `legacy` somente por existir código Java.

## Tipos e interoperabilidade

- Preferir propriedades imutáveis e tipos próprios para conceitos de negócio.
- Usar `data class` principalmente para valores, commands, queries e DTOs
  imutáveis; avaliar identidade e mutabilidade antes de usá-la em entities.
- Tornar explícita a nullability nas fronteiras Java/Kotlin. Evitar propagar
  platform types ambíguos e usar `!!` para ocultar um contrato indefinido.
- Tratar interoperabilidade na API da classe. Não duplicar abstrações por
  diferença de linguagem.
- Preservar os plugins e as opções Kotlin definidos no build ao trabalhar com
  proxies Spring e JPA; verificar requisitos de abertura e construção de
  entidades antes de adicionar configuração manual equivalente.

## Injeção e framework

Preferir injeção por construtor com dependências explícitas. Evitar field
injection e service locators que escondam dependências.

Objetos de domínio permanecem livres de anotações Spring e JPA. Casos de uso
podem usar `@Service` e `@Transactional`; adapters usam anotações próprias da
tecnologia. Não introduzir uma abstração de DI apenas para remover essas
anotações das camadas que já aceitam Spring.

## Compatibilidade

Antes de usar uma API ou adicionar dependência, conferir o build da aplicação,
o toolchain Java e as versões Kotlin/Spring efetivamente configuradas. A versão
de Java disponível no terminal pode diferir da versão exigida pelo toolchain.
Usar o Gradle Wrapper do projeto e preservar o modelo de execução existente,
incluindo suas fronteiras entre operações bloqueantes e reativas.
