# Se existe Kotlin, por que ainda usamos Java?

*O que muda no código de uma API Spring Boot — e por que a escolha envolve mais do que escrever menos linhas.*

Você abre um controller Kotlin e encontra a dependência, o construtor e a propriedade declarados na mesma linha. Logo depois, vê um DTO sem getters escritos à mão e uma consulta que trata a ausência com `?:`.

É fácil pensar: se conseguimos expressar tudo isso com menos código, por que ainda usamos Java?

A resposta envolve o que a linguagem oferece e o que a equipe precisa manter. Kotlin pode simplificar tarefas recorrentes; adotar outra linguagem também exige aprendizado, ajustes de integração e critérios de revisão.

Vamos acompanhar uma API de customers com Spring Boot, da configuração à persistência, mostrando primeiro Java e depois Kotlin. O objetivo é entender o que muda em cada etapa e onde essa diferença ajuda.

> **Espaço para imagem de abertura — opcional**  
> **Briefing:** ilustração horizontal com dois caminhos, identificados como Java e Kotlin, chegando à mesma JVM. Dar o mesmo peso visual às linguagens, sem pódio ou ideia de vencedora.  
> **Alt-text:** “Java e Kotlin como dois caminhos para executar uma aplicação na JVM.” Se a imagem for apenas decorativa e repetir essa informação do texto, usar `alt=""`.

## Java cresceu. Kotlin também.

Java foi apresentado pela Sun em 1995. A JetBrains anunciou Kotlin em 2011 e lançou a versão 1.0 em 2016, com foco em interoperabilidade, segurança e clareza. No backend JVM, Kotlin aproveita a plataforma e o ecossistema que Java ajudou a construir. [História do Java](https://www.java.com/download/help/whatis_java.html), [anúncio do Kotlin](https://blog.jetbrains.com/kotlin/2021/08/ten-years-of-kotlin/) e [lançamento do Kotlin 1.0](https://blog.jetbrains.com/kotlin/2016/02/kotlin-1-0-released-pragmatic-language-for-jvm-and-android/).

Na prática, Java segue fazendo sentido quando a equipe já o domina e a aplicação atende bem ao negócio. Reescrever código estável exige uma justificativa maior do que economizar linhas. E Java também evoluiu: `record`, por exemplo, reduz bastante o código de objetos que transportam dados. [Records em Java](https://dev.java/learn/records/).

Kotlin entra nessa decisão oferecendo outras formas de expressar o mesmo trabalho. Vamos vê-las no código.

## Configuração: a JVM continua aqui

Os exemplos deste artigo retratam a versão anterior do template, com Spring MVC e JPA.
O repositório foi migrado para WebFlux/Reactor/R2DBC em 02/10/2026; a stack e os
contratos atuais estão no [README](../../README.md#execução-reativa).
Os trechos abaixo preservam a comparação original entre Java e Kotlin. O projeto exige Java 25 como toolchain — usar Kotlin não elimina o JDK. As versões abaixo são as do projeto usado neste artigo.

**Java — base no `build.gradle` (Groovy):**

```groovy
plugins {
    id 'java'
    // ... plugins Spring Boot e demais configurações
}

java {
    toolchain { languageVersion = JavaLanguageVersion.of(25) }
}
```

**Kotlin — plugins presentes no template:**

```groovy
plugins {
    id 'org.jetbrains.kotlin.jvm' version '2.3.21'
    id 'org.jetbrains.kotlin.plugin.spring' version '2.3.21'
    id 'org.jetbrains.kotlin.plugin.jpa' version '2.3.21'
    // ... plugins Spring Boot e demais configurações
}
```

O toolchain permanece igual. O plugin `spring` permite que o framework crie proxies das classes anotadas, que em Kotlin seriam finais por padrão. O plugin `jpa` gera o construtor sem argumentos usado pela persistência. O template também configura `allOpen` para as anotações JPA. [Plugin Spring/all-open](https://kotlinlang.org/docs/all-open-plugin.html) e [plugin JPA/no-arg](https://kotlinlang.org/docs/no-arg-plugin.html).

As dependências web, JPA e PostgreSQL continuam. Para Kotlin, o template inclui `kotlin-reflect` e `jackson-module-kotlin`. Já `spring.jpa.open-in-view: false` vale para ambas as linguagens: essa decisão pertence à integração com a persistência.

**Como ler os exemplos:** os trechos Kotlin foram recortados ou reduzidos a partir do template; os de Java são versões alternativas do mesmo fluxo, usando recursos modernos. `// ...` indica imports, campos ou comportamentos omitidos para destacar a comparação. Os recortes precisam ser completados para executar uma aplicação.

## Endpoint: construtor e dependência no mesmo lugar

Vamos consultar `GET /api/customers/{id}`. O proprietário vem do usuário autenticado, como no template.

**Java:**

```java
@RestController
@RequestMapping("/api/customers")
public class CustomerController {
    private final FindCustomer find;

    public CustomerController(FindCustomer find) {
        this.find = find;
    }

    @GetMapping("/{id}")
    public CustomerResponse find(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id) {
        var query = new FindCustomerQuery(id, UUID.fromString(jwt.getSubject()));
        return CustomerResponse.from(find.execute(query));
    }
    // ... outros endpoints
}
```

**Kotlin:**

```kotlin
@RestController
@RequestMapping("/api/customers")
class CustomerController(private val find: FindCustomer) {
    @GetMapping("/{id}")
    fun find(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable id: UUID
    ) = CustomerResponse.from(
        find.execute(FindCustomerQuery(id, UUID.fromString(jwt.subject)))
    )
    // ... outros endpoints
}
```

`private val find` declara o parâmetro do construtor e a propriedade de uma vez. O Spring injeta a dependência pelo construtor. `val` impede reatribuir a referência; não torna o objeto inteiro imutável.

A função usa `=` porque seu corpo é uma expressão, e o compilador infere o retorno. Até o getter Java `getSubject()` pode ser acessado como `jwt.subject`.

O contrato HTTP permanece igual. O ganho está na declaração da classe e na forma de acessar os dados. A responsabilidade de validar o proprietário continua existindo nas duas versões.

### E quando recebemos dados?

Na criação de customer, o request contém nome e email. Aqui Java moderno já oferece uma alternativa curta.

**Java:**

```java
public record CreateCustomerRequest(
    @NotBlank @Size(max = 100) String name,
    @NotBlank @Email @Size(max = 254) String email
) {}
```

**Kotlin:**

```kotlin
data class CreateCustomerRequest(
    @field:NotBlank @field:Size(max = 100)
    val name: String,
    @field:NotBlank @field:Email @field:Size(max = 254)
    val email: String
)
```

Nos dois casos, o controller recebe o corpo com `@Valid @RequestBody`. Em Kotlin, `@field:` coloca a anotação explicitamente no campo gerado, onde a validação pode encontrá-la. Uma propriedade pode gerar campo, getter e parâmetro de construtor; escolher o alvo faz parte da integração com o framework. [Alvos de anotações](https://kotlinlang.org/docs/annotations.html#annotation-use-site-targets).

`String` impede valores nulos no contrato Kotlin, mas ainda aceita uma string vazia. Por isso, `@NotBlank` continua necessário. Segurança de tipos e validação de conteúdo resolvem problemas diferentes.

## Caso de uso: ausência faz parte do tipo

O controller chama `FindCustomer`. No template, esse caso de uso verifica a revisão no banco e consulta o cache. Aqui recortamos a leitura do customer; a transação continua no caso de uso.

**Java — considerando um repositório que retorna `Optional<Customer>`:**

```java
@Transactional
public CustomerDetails execute(FindCustomerQuery query) {
    // ... verificação de revisão e consulta ao cache
    var customer = customers
        .find(new CustomerId(query.id()), query.ownerId())
        .orElseThrow(CustomerNotFound::new);
    // ... preenchimento do cache
    return CustomerDetails.from(customer);
}
```

**Kotlin — o repositório do template retorna `Customer?`:**

```kotlin
@Transactional
fun execute(query: FindCustomerQuery): CustomerDetails {
    // ... verificação de revisão e consulta ao cache
    val customer = customers.find(CustomerId(query.id), query.ownerId)
        ?: throw CustomerNotFound()
    // ... preenchimento do cache
    return CustomerDetails.from(customer)
}
```

As duas versões tratam a ausência. Em Kotlin, `Customer?` permite `null`, enquanto `Customer` exige um valor não nulo. O operador Elvis, `?:`, executa a alternativa quando o resultado é nulo.

O ganho aparece na consistência desse contrato pela linguagem. Ainda é preciso cuidar das fronteiras com Java e evitar `!!`, que força a aceitação de um valor e pode causar uma exceção se ele for nulo. [Null safety em Kotlin](https://kotlinlang.org/docs/null-safety.html).

Em bibliotecas Java sem informações suficientes de nullability, Kotlin pode receber *platform types*: tipos cuja possibilidade de `null` não está totalmente definida para o compilador. Interoperabilidade facilita a adoção, mas essas fronteiras ainda precisam de atenção.

> **Espaço para imagem do fluxo — opcional**  
> **Briefing:** diagrama horizontal simples: “GET /api/customers/{id} → CustomerController → FindCustomer → CustomerRepository → JpaCustomerRepository → PostgreSQL”. Marcar “transação” ao redor do caso de uso e de seu acesso à persistência. Evitar árvore de pacotes.  
> **Alt-text:** “Consulta de customer: o endpoint chama o controller, que executa FindCustomer e acessa PostgreSQL pela implementação de CustomerRepository. A transação começa no caso de uso.”

## Repositório: interface, implementação e `override`

**Java:**

```java
public interface CustomerRepository {
    Optional<Customer> find(CustomerId id, UUID ownerId);
    // ... outras operações
}

@Repository
public class JpaCustomerRepository implements CustomerRepository {
    // ... dependências e construtor
    @Override
    public Optional<Customer> find(CustomerId id, UUID ownerId) {
        return repository.findByIdAndOwnerId(id.getValue(), ownerId)
            .map(this::toDomain);
    }
    // ... conversão para o domínio
}
```

**Kotlin:**

```kotlin
interface CustomerRepository {
    fun find(id: CustomerId, ownerId: UUID): Customer?
    // ... outras operações
}

@Repository
class JpaCustomerRepository(
    private val repository: SpringDataCustomerRepository
    // ... outras dependências
) : CustomerRepository {
    override fun find(id: CustomerId, ownerId: UUID): Customer? =
        repository.findByIdAndOwnerId(id.value, ownerId)?.toDomain()
    // ... conversão para o domínio
}
```

No exemplo Java, o método Spring Data também retorna `Optional`; no template Kotlin, retorna `CustomerJpaEntity?`. Kotlin usa `:` para implementar a interface e exige `override` na implementação. `?.` chama a conversão apenas quando existe uma entidade.

O template ainda envolve a operação com métricas, omitidas aqui. Sua conversão é uma extensão privada, `fun CustomerJpaEntity.toDomain()`: permite escrever `entity.toDomain()` sem acrescentar um método à classe original.

Extensões são úteis para conversões pequenas como essa. Elas não modificam a classe original e não têm acesso aos seus membros privados. A função continua declarada no ponto em que a transformação acontece. [Funções de extensão](https://kotlinlang.org/docs/extensions.html).

### A entidade JPA também muda

Vamos reduzir `CustomerJpaEntity` a ID e nome. O template completo também guarda proprietário, email, revisão e datas.

**Java:**

```java
@Entity
@Table(name = "customers")
public class CustomerJpaEntity {
    @Id
    private UUID id;
    @Column(nullable = false, length = 100)
    private String name;

    protected CustomerJpaEntity() {}

    public CustomerJpaEntity(UUID id, String name) {
        this.id = id;
        this.name = name;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    // ... demais campos
}
```

**Kotlin:**

```kotlin
@Entity
@Table(name = "customers")
class CustomerJpaEntity(
    @Id val id: UUID,
    @Column(nullable = false, length = 100) var name: String
    // ... demais campos
)
```

Agora dá para ver o papel da configuração inicial: `plugin.jpa` gera o construtor sem argumentos para o framework, e `allOpen` permite herança e proxies nas classes JPA. Essas necessidades continuam existindo mesmo quando o código não as declara manualmente.

Para o código consumidor, `entity.name = novoNome` usa o setter gerado; `entity.id` permite leitura. No exemplo Java, o ID também tem somente getter público. Lombok poderia reduzir parte dessa escrita em Java, acrescentando sua própria ferramenta ao build.

## Model: getters sem repetição, setters com controle

No domínio, `Customer` permite ler o nome, mas controla sua alteração por métodos de negócio. Recortando apenas essa propriedade:

**Java:**

```java
public class Customer {
    private String name;

    public Customer(String name /* ... demais parâmetros */) {
        Objects.requireNonNull(name);
        if (name.isBlank() || name.length() > 100) {
            throw new IllegalArgumentException("Nome inválido");
        }
        this.name = name;
        // ... demais validações
    }

    public String getName() { return name; }
    // ... update(...) valida e altera o nome; sem setter público
}
```

**Kotlin:**

```kotlin
class Customer(name: String /* ... demais parâmetros */) {
    var name: String = name
        private set

    init {
        require(name.isNotBlank() && name.length <= 100) { "Nome inválido" }
        // ... demais validações
    }
    // ... update(...) com regras de negócio
}
```

`var` permite alteração, mas `private set` restringe o setter à classe. Quem usa o model escreve `customer.name` para ler e chama `update(...)` para alterar. Um parâmetro sem `val` ou `var`, como `name` no construtor acima, não declara automaticamente uma propriedade.

O bloco `init` executa durante a construção normal do objeto. `require` lança `IllegalArgumentException` quando a condição falha. No template, a verificação do nome fica em uma função reutilizada na construção e na atualização; aqui a colocamos diretamente no `init` para mostrar o mecanismo. [Construtores e inicialização](https://kotlinlang.org/docs/classes.html#constructors-and-initializer-blocks) e [contrato de require](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/require.html).

Essa regra protege o model mesmo quando ele é criado fora do endpoint HTTP. Também aparece uma escolha de design: o DTO pode ser imutável, enquanto um customer permite alterações controladas. Kotlin dá recursos para expressar ambas as situações.

`Customer` e `CustomerJpaEntity` são classes comuns no template. Igualdade por campos e cópia precisam fazer sentido antes de escolher `data class` para um objeto com identidade e estado mutável.

## Resposta: `record`, `data class` e `companion object`

Para transportar dados, Java moderno também é conciso. Abaixo reduzimos a resposta a três campos; no template ela inclui revisão e datas.

**Java:**

```java
public record CustomerResponse(UUID id, String name, String email) {
    public static CustomerResponse from(CustomerDetails details) {
        return new CustomerResponse(details.id(), details.name(), details.email());
    }
}
```

**Kotlin:**

```kotlin
data class CustomerResponse(
    val id: UUID,
    val name: String,
    val email: String
) {
    companion object {
        fun from(details: CustomerDetails) =
            CustomerResponse(details.id, details.name, details.email)
    }
}
```

Ambos geram recursos como igualdade por componentes e representação textual. A `data class` também oferece `copy()`. Essa cópia é rasa, e propriedades `val` não garantem imutabilidade dos objetos referenciados. [Data classes](https://kotlinlang.org/docs/data-classes.html).

O `companion object` reúne funções associadas à classe, permitindo chamar `CustomerResponse.from(...)` em Kotlin. Ele é um objeto, não um `static` Java literal: ao chamar essa função a partir de Java, usa-se `CustomerResponse.Companion.from(...)`, ou `@JvmStatic` para gerar também o acesso estático. [Companion objects](https://kotlinlang.org/docs/object-declarations.html#companion-objects).

A mesma ideia aparece na fábrica `Customer.register(...)` do template, que normaliza dados e cria o estado inicial. Para uma função independente de uma classe, Kotlin também permite declará-la diretamente no arquivo, sem criar uma classe utilitária só para hospedá-la.

## Coleções: a diferença aparece na listagem

Ao listar customers, cada resultado precisa virar uma resposta HTTP. Recortando a conversão feita pelo controller:

**Java:**

```java
return list.execute(query).stream()
    .map(CustomerResponse::from)
    .toList();
```

**Kotlin:**

```kotlin
return list.execute(query).map(CustomerResponse::from)
```

As duas versões usam uma referência à função de conversão. Kotlin oferece `map` diretamente para a coleção e já devolve uma lista com os resultados. Para esse caso simples, elimina duas etapas visíveis. [Transformações de coleções](https://kotlinlang.org/docs/collection-transformations.html).

Isso não estabelece uma vantagem automática de desempenho: o `map` usado nessa coleção Kotlin é imediato, enquanto operações intermediárias de Stream são preguiçosas. Fluxos maiores exigem avaliar o comportamento e as alocações. [Execução de Streams](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/stream/package-summary.html).

Também podemos deixar mais clara a construção da query com argumentos nomeados:

**Java:**

```java
var query = new ListCustomersQuery(ownerId, null, 20);
```

**Kotlin:**

```kotlin
val query = ListCustomersQuery(ownerId = ownerId, after = null, limit = 20)
```

O leitor entende o significado de `null` e `20` sem abrir a declaração. Esse uso é possível porque a query está declarada em Kotlin; argumentos nomeados não estão disponíveis ao chamar métodos Java. [Argumentos nomeados](https://kotlinlang.org/docs/functions.html#named-arguments).

## O que melhora na prática?

Neste fluxo, os ganhos aparecem em três lugares: menos declarações repetidas nos construtores e nas propriedades; contratos explícitos sobre ausência de valores; e transformações pequenas que ficam próximas dos dados.

Isso pode facilitar alterações e revisões. Ao acrescentar uma dependência, uma propriedade ou uma conversão, há menos trechos mecânicos para manter sincronizados. O compilador também ajuda a exigir o tratamento de valores nulos onde o contrato permite ausência.

Java com `record`, `Optional` e Lombok também resolve parte dessas dificuldades. A vantagem de Kotlin precisa ser avaliada no conjunto do fluxo e na clareza que a equipe consegue preservar.

## Então por que ainda usamos Java?

Porque as facilidades que vimos têm um custo de adoção, e Java já pode atender bem ao projeto.

Uma equipe que conhece Java, suas ferramentas e seus modos de falha pode manter uma aplicação com mais confiança do que manteria o mesmo sistema em uma linguagem que ainda está aprendendo. Familiaridade pesa especialmente na revisão de código, na investigação de problemas e na entrada de novas pessoas.

Em Kotlin, a equipe passa a cuidar também das versões do compilador e de seus plugins, dos alvos de anotações e dos contratos vindos de bibliotecas Java. Os exemplos de JPA e validação mostram que a integração continua exigindo conhecimento do framework.

Há também decisões de estilo. Encadear várias funções como `let`, `apply` e `run` pode produzir código curto e difícil de seguir. Convenções simples e revisões cuidadosas ajudam a preservar a clareza que motivou a escolha da linguagem.

Desempenho depende do que o programa faz, das bibliotecas e das alocações envolvidas. Neste template, HTTP usa Spring MVC e JPA executa chamadas bloqueantes. Trocar Java por Kotlin, por si só, não altera esse modelo de execução.

Se essas dificuldades estão sob controle e a equipe vê valor nos recursos de Kotlin, a adoção pode começar em uma parte pequena da aplicação. Java e Kotlin podem coexistir no mesmo projeto JVM, permitindo validar a decisão sem uma reescrita completa. [Interoperabilidade Java/Kotlin](https://kotlinlang.org/docs/java-interop.html).

Um fluxo de customer já oferece uma boa experiência inicial: construtor, interface, DTO, propriedade, tratamento de ausência e persistência. A pergunta para a equipe é concreta: conseguimos escrever, revisar e manter esse fluxo com mais clareza? A resposta ajuda a decidir onde Kotlin merece entrar no projeto — e onde Java continua atendendo bem.
