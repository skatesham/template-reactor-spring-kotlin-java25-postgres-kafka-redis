# Vulnerabilidades de dependências

Revisão do relatório Mend/IDE em 01/10/2026. As versões são selecionadas em
`build.gradle`: propriedades do BOM do Spring Boot mantêm Jackson, Tomcat e
Logback alinhados; constraints atualizam os codecs transitivos do Kafka.
Remover os overrides quando o BOM passar a fornecer versões corrigidas iguais
ou superiores, conferindo também o classpath de testes.

| Dependência reportada | Versão selecionada | Referência da correção |
| --- | --- | --- |
| Jackson 3 core/databind e módulo Kotlin | 3.1.7 | [Release 3.1.7](https://github.com/FasterXML/jackson/wiki/Jackson-Release-3.1.7) |
| Jackson 2 core/databind e demais módulos do BOM | 2.21.7 | [Release 2.21.7](https://github.com/FasterXML/jackson/wiki/Jackson-Release-2.21.7) |
| Tomcat core/websocket/EL | 11.0.26 | [Avisos Tomcat 11](https://tomcat.apache.org/security-11.html) |
| Logback classic/core | 1.6.3 | [Release 1.6.3](https://logback.qos.ch/news.html#1.6.3) |
| zstd-jni | 1.5.7-14 | [Release 1.5.7-14](https://github.com/luben/zstd-jni/releases/tag/v1.5.7-14) |
| at.yawk.lz4:lz4-java | 1.11.1 | [Release 1.11.1](https://github.com/yawkat/lz4-java/releases/tag/v1.11.1) |

Jackson 3.1.6/2.21.6 corrigem parte dos alertas; 3.1.7/2.21.7 incluem também
CVE-2026-89407, CVE-2026-89425, CVE-2026-91776 e CVE-2026-91777.
Tomcat 11.0.25 ainda é afetado por várias falhas do relatório, incluindo o
bypass de segurança de WebSocket CVE-2026-76183; usar 11.0.26.

## Broker Kafka removido do classpath de testes

O starter `spring-boot-starter-kafka-test` foi removido por não ser utilizado.
Ele trazia `spring-kafka-test` e o broker `kafka_2.13:4.2.1`. Os testes existentes
usam `KafkaContainer` e não usam `EmbeddedKafka` nem utilitários de
`spring-kafka-test`. O cliente Kafka e os testes com broker real permanecem.

Para clusters externos, CVE-2026-41115 continua exigindo revisão de ACLs:
conforme a [Apache](https://kafka.apache.org/community/cve-list/), a implementação
está correta e a documentação de permissões foi corrigida.
`CONSUMER_GROUP_DESCRIBE` exige `DESCRIBE` no recurso `GROUP`. Trocar a versão
do JAR não resolve permissões incorretas. O Compose e os containers de teste
usam um broker local PLAINTEXT sem ACLs; ACLs de ambientes externos não são
gerenciadas por este repositório.

## Pendência

- **Snappy / CVE-2026-90559:** não há versão corrigida publicada confirmada.
  O [issue upstream #738](https://github.com/xerial/snappy-java/issues/738)
  permanece aberto e reporta a falha até em 1.1.10.8. A dependência transitiva
  1.1.10.7 permanece: atualizar para 1.1.10.8 não elimina o alerta. Excluí-la
  impede o cliente Kafka de ler mensagens comprimidas com Snappy. O produtor
  deste projeto usa a compressão padrão `none`, mas isso não impede consumo de
  mensagens Snappy enviadas por outros produtores. Acompanhar o upstream e
  controlar os produtores com acesso aos tópicos; não suprimir o alerta como
  corrigido. A compressão das mensagens externas e existentes é desconhecida;
  por isso o suporte a Snappy foi preservado. Antes de excluir a dependência,
  inventariar produtores, configurações de compressão dos tópicos/brokers e
  mensagens retidas; mudar apenas a configuração do produtor atual não
  converte dados já armazenados.

### Alcance do método citado na CVE-2026-90559

O [cliente Kafka 4.2.1](https://github.com/apache/kafka/blob/4.2.1/clients/src/main/java/org/apache/kafka/common/compress/SnappyCompression.java)
usa `SnappyInputStream` ao consumir mensagens Snappy. Na
[versão 1.1.10.7](https://github.com/xerial/snappy-java/blob/v1.1.10.7/src/main/java/org/xerial/snappy/SnappyInputStream.java),
esse stream calcula o tamanho descomprimido, aloca/reutiliza um `byte[]`
suficiente e chama `Snappy.uncompress(byte[], int, int, byte[], int)`.
Não chama a sobrecarga `Snappy.uncompress(ByteBuffer, ByteBuffer)` citada na
CVE. A cadeia foi conferida também no bytecode dos JARs resolvidos pelo Gradle.
O código da aplicação não contém chamadas diretas ao Snappy.

Assim, não foi identificado acesso ao método específico vulnerável pelo fluxo
Kafka atual. Isso não corrige a biblioteca nem comprova ausência de outras
falhas. Os testes funcionais anteriores não foram uma prova de exploração ou
uma análise dinâmica de cobertura do método vulnerável. Manter essa avaliação
restrita a esta CVE e revisar se mudarem as dependências ou os usos do Snappy.

## Verificação

Usar JDK 25 e `./gradlew`. Conferir `runtimeClasspath` e `testRuntimeClasspath`
com `dependencies`, executar os testes HTTP e de integração existentes e gerar
`bootJar`. Reexecutar o scanner Mend/IDE após sincronizar o Gradle para confirmar
o resultado no relatório; resolução de dependências e testes não substituem
uma nova varredura.

Validação da atualização de versões com JDK 25: classpaths da aplicação e dos testes
resolvidos sem dependências ausentes; `bootJar` gerado e inspecionado para
confirmar as versões empacotadas; 62 testes aprovados, sem falhas ou skips:

```sh
./gradlew test \
  --tests 'com.kotlin.template.identity.AuthHttpTests' \
  --tests 'com.kotlin.template.TemplateApplicationTests' \
  --tests 'com.kotlin.template.customer.CustomerFlowIntegrationTests' \
  --tests 'com.kotlin.template.api.RestAssuredIntegrationTests' \
  bootJar
```

A varredura Mend/IDE ainda precisa ser reexecutada.

Após remover o starter Kafka de testes, `testRuntimeClasspath` não contém
`spring-kafka-test`, `kafka_2.13` ou `kafka-test-common-runtime`. Os 13 testes de
`TemplateApplicationTests` e `CustomerFlowIntegrationTests` passaram novamente
com o broker do Testcontainers, sem falhas ou skips.
