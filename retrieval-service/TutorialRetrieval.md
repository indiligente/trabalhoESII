# Tutorial de Revisão — retrieval-service

> **Papel deste documento:** orientação de mentor. Não há soluções prontas aqui — apenas apontamentos de onde olhar e perguntas para guiar seu raciocínio.

---

## 1. Classe principal ausente (CRÍTICO)

Todo projeto Spring Boot precisa de um ponto de entrada: uma classe anotada com `@SpringBootApplication` que contenha o método `main`.

Olhe o arquivo `TfApplicationTests.java`. Ele usa `@SpringBootTest`, que pressupõe que essa classe principal exista em algum lugar do pacote pai. Agora pergunte a si mesmo: **ela existe?** Tente localizar qualquer arquivo `.java` no `src/main` que não seja o `Retrieval.java` ou o `RetrievalController.java`.

> **Dica:** sem essa classe, a aplicação simplesmente não inicia — nem em desenvolvimento, nem no Docker.

---

## 2. Imports ausentes no Controller (CRÍTICO)

Abra o `RetrievalController.java` e conte quantas anotações e tipos estão sendo usados **sem o import correspondente** no topo do arquivo.

Faça uma lista de cada símbolo que aparece no corpo da classe e verifique se há um `import` para ele. Alguns pontos para começar sua investigação:

- `@RequestMapping` — de onde vem?
- `@Autowired` — de onde vem?
- `@PostMapping` e `@RequestBody` — de onde vem?
- `@RequestParam` — de onde vem?
- `Map` — de onde vem?

> **Dica:** compare com o `Retrieval.java`, que tem seus imports declarados corretamente. Use-o de referência para entender o padrão.

---

## 3. Tipo inexistente: `Documento` (CRÍTICO)

No método `pesquisar` do controller, o tipo de retorno é `List<Documento>`. Pergunta: **essa classe `Documento` existe em algum lugar do projeto?**

Agora pense: o `Retrieval.java` já retorna algo do Spring AI. Qual é o tipo real que ele devolve? Esse tipo precisa ser o mesmo que o controller declara como retorno.

> **Dica:** busque no `Retrieval.java` qual classe é usada no `List<...>` do método `buscarDocumentos`.

---

## 4. Nome da pasta Java com espaço (CRÍTICO)

O explorador reportou que a pasta Java se chama `retrieval service` (com espaço). No entanto, a declaração de pacote nos arquivos `.java` usa `retrieval_service` (com underscore).

Em Java, **a estrutura de pastas deve corresponder exatamente ao nome do pacote**. Um espaço em nome de pasta não é um identificador Java válido.

> **Dica:** renomeie a pasta para que corresponda exatamente ao que está no `package` de cada arquivo.

---

## 5. Dependência web ausente no `pom.xml` (CRÍTICO)

O controller usa `@RestController`, `@PostMapping`, `@GetMapping` — funcionalidades do Spring MVC. Agora abra o `pom.xml` e responda: **existe alguma dependência que inicie o Spring MVC ou o Spring Web?**

Pense em qual starter do Spring Boot é responsável por habilitar um servidor HTTP e as anotações de controller.

> **Dica:** o fato de outras dependências (Spring AI, por exemplo) existirem não garante que o Spring Web seja ativado automaticamente.

---

## 6. `application.yaml` — Estrutura quebrada e configurações incorretas (IMPORTANTE)

O arquivo `application.yaml` tem vários problemas de indentação e conteúdo. Analise com calma:

### 6a. Typo no nome da propriedade
Procure a linha que menciona `vextorstore`. Compare com o nome correto da propriedade do Spring AI para o Qdrant. Quantas letras estão trocadas?

### 6b. Configuração de Gateway num serviço de Retrieval
Existe um bloco `spring.cloud.gateway.server.webflux` no yaml. Pergunte a si mesmo: **este serviço é um API Gateway?** Se não for, por que essa configuração está aqui? De onde ela pode ter sido copiada?

### 6c. Configuração do Ollama ausente
O `pom.xml` declara `spring-ai-starter-model-ollama`. Para o Spring AI saber como se conectar ao Ollama, ele precisa de alguma configuração no yaml. Alguma propriedade `spring.ai.ollama.*` existe no arquivo?

> **Dica:** sem essa configuração, o Spring AI não sabe onde está o servidor Ollama.

### 6d. Cliente Eureka configurado sem dependência
O yaml tem `eureka.client.service-url.defaultZone`. Agora olhe o `pom.xml`: existe algum starter do Spring Cloud Eureka na lista de dependências (não no `dependencyManagement`, mas em `<dependencies>`)?

---

## 7. Import incorreto e imports não utilizados (MENOR)

### 7a. Em `Retrieval.java`
Há um import de `javax.naming.directory.SearchResult`. Pesquise o que essa classe representa. Ela faz parte do mundo JNDI/LDAP — não tem nada a ver com o Spring AI. Além disso, verifique: ela é usada em algum lugar do arquivo?

### 7b. Em `RetrievalController.java`
`Logger`, `LoggerFactory` e `Environment` são importados. Eles aparecem em alguma variável ou método do controller?

> **Dica:** imports não utilizados não quebram o projeto, mas indicam que o código foi iniciado/copiado sem revisão cuidadosa. É uma boa prática removê-los.

---

## 8. Inconsistência de versão no Dockerfile (MENOR)

No `Dockerfile`, o estágio de **build** usa `eclipse-temurin:25-jdk-alpine`, enquanto o estágio de **produção** usa `eclipse-temurin:21-jre-alpine`.

Pergunta: faz sentido compilar com uma versão do JDK e executar com outra? Qual versão de Java o `pom.xml` declara como target?

---

## 9. Dependência Redis — necessária mesmo? (PARA REFLEXÃO)

O `pom.xml` inclui `spring-boot-starter-session-data-redis`. Pense na responsabilidade deste serviço: ele indexa e busca documentos num vector store. **Qual seria o papel do Redis aqui?**

Se você não tem uma resposta clara, talvez essa dependência tenha sido incluída sem necessidade real.

---

## Resumo dos pontos por prioridade

| Prioridade | Problema |
|---|---|
| CRÍTICO | Classe `@SpringBootApplication` ausente |
| CRÍTICO | Múltiplos imports ausentes no controller |
| CRÍTICO | Tipo `Documento` não existe — referência quebrada |
| CRÍTICO | Nome de pasta com espaço incompatível com package |
| CRÍTICO | `spring-boot-starter-web` ausente no `pom.xml` |
| IMPORTANTE | Typo `vextorstore` no yaml |
| IMPORTANTE | Configuração de Gateway num serviço de Retrieval |
| IMPORTANTE | Configuração do Ollama ausente no yaml |
| IMPORTANTE | Eureka client sem dependência declarada |
| MENOR | Import JNDI/LDAP incorreto e imports não utilizados |
| MENOR | Inconsistência de versão JDK no Dockerfile |
| REFLEXÃO | Redis realmente é necessário aqui? |

---

> Comece pelos itens **CRÍTICOS** — sem eles a aplicação não compila nem inicia. Resolva um de cada vez e valide com `./mvnw compile` entre cada correção.
