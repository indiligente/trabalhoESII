# Tutorial: Implementando um Agent Service com Microserviços

> **Baseado no código de referência `EXCLUIRDEPOIS.java`**
> Java 21 · Spring Boot · Spring AI · Ollama · Qdrant · Eureka

---

## O que você vai aprender

1. O que é um microserviço e como este projeto está estruturado
2. Como criar os **Endpoints** (rotas de entrada) do serviço
3. Como funciona o **Ciclo Agêntico**: Raciocínio → Ação → Observação
4. Como os pedaços se conectam: DTO → Config → Tools → Service → Controller

---

## Parte 0 — Entendendo a Arquitetura

Antes de escrever código, entenda o mapa do sistema:

```
[Usuário / Frontend]
        │
        │  HTTP POST /api/agent/chat
        ▼
┌──────────────────┐        registro
│  agent-service   │ ──────────────────► Eureka (naming-server :8765)
│  porta: 8090     │
│                  │
│  Controller      │ ← recebe HTTP
│  Service         │ ← executa o ciclo agêntico
│  Tools           │ ← ações que o agente pode fazer
│  Config          │ ← configura o LLM
└──────┬───────────┘
       │ conversa              │ busca vetorial
       ▼                       ▼
  Ollama (LLM local)       Qdrant (banco vetorial)
  porta: 11434              porta: 6334
```

**Por que isso é um microserviço?**
- Tem **uma responsabilidade única**: processar mensagens com IA
- Roda em **porta própria** (8090)
- Se **registra autonomamente** no Eureka para que outros serviços o encontrem pelo nome `agent-service`
- Pode ser **escalado independentemente** dos demais

---

## Parte 1 — Os DTOs (Data Transfer Objects)

**O que são?** São as "embalagens" que transportam dados entre o usuário e o serviço. Pense neles como o formulário que você preenche e a resposta que recebe.

### `AgentRequest.java` — O que o usuário envia

```java
package TF_ESII.TF.DTO;

import java.util.UUID;

public record AgentRequest(
    String message,    // "Quais serviços estão online?"
    String sessionId   // "abc-123" (identifica a conversa)
) {
    // Compact constructor do Java 21: executa na criação do record
    public AgentRequest {
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString(); // cria sessão nova automaticamente
        }
    }
}
```

**Exemplo real de JSON que o usuário envia:**
```json
{
  "message": "Quais serviços estão registrados no Eureka?",
  "sessionId": "minha-conversa-001"
}
```

**Por que `sessionId`?**
Imagine que você quer ter uma conversa de múltiplos turnos:
- Turno 1: "Qual é o status do serviço X?" → resposta
- Turno 2: "E o Y?" ← o agente precisa lembrar do contexto do turno 1

O `sessionId` é o fio que conecta as mensagens de uma mesma conversa.

---

### `AgentResponse.java` — O que o serviço devolve

```java
package TF_ESII.TF.DTO;

import java.util.List;

public record AgentResponse(
    String sessionId,        // mesmo ID da requisição
    String answer,           // "Os serviços registrados são: X, Y, Z"
    List<String> toolsUsed,  // ["buscarNaBaseDeConhecimento"]
    int iterations           // quantas voltas o ciclo agêntico deu
) {}
```

**Exemplo de resposta JSON:**
```json
{
  "sessionId": "minha-conversa-001",
  "answer": "Os serviços registrados são: payment-service e user-service.",
  "toolsUsed": ["buscarNaBaseDeConhecimento"],
  "iterations": 2
}
```

`toolsUsed` e `iterations` são metadados de debug — eles dizem **o que o agente fez por baixo dos panos** para chegar na resposta.

---

## Parte 2 — A Configuração (`AgentConfig.java`)

**O que faz?** Cria o `ChatClient`, que é o objeto que o serviço usa para conversar com o Ollama (o LLM rodando localmente).

```java
package TF_ESII.TF.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration  // avisa o Spring: "esta classe tem beans para registrar"
public class AgentConfig {

    @Bean  // Spring vai criar este objeto e guardar no contexto para injetar onde precisar
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
            .defaultSystem("""
                Você é um assistente inteligente com acesso a ferramentas.
                SEMPRE verifique a base de conhecimento antes de responder.
                Responda em português, de forma clara e objetiva.
                Quando não souber algo, diga claramente ao invés de inventar.
                """)
            .build();
    }
}
```

**O que é o `defaultSystem`?**
É o "manual de instruções" que o LLM recebe antes de qualquer conversa. Ele define a personalidade e as regras de comportamento do agente.

**Analogia:** É como briefar um funcionário novo no primeiro dia: "Você trabalha aqui, tem acesso a esses sistemas, e deve agir desta forma."

---

## Parte 3 — As Ferramentas (`AgentTools.java`)

**O que são?** São as **ações concretas** que o agente pode executar. O LLM decide quando usar cada uma; o código Java executa de fato.

```java
package TF_ESII.TF.agent;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

@Component  // registra como bean do Spring
public class AgentTools {

    private final VectorStore vectorStore; // banco vetorial Qdrant

    public AgentTools(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    // @Tool expõe este método ao LLM como uma ação disponível
    // A "description" é lida pelo LLM para decidir quando usar esta tool
    @Tool(description = "Busca informações relevantes na base de conhecimento. "
        + "Use sempre que precisar de dados específicos do domínio.")
    public String buscarNaBaseDeConhecimento(
        @ToolParam(description = "A consulta em linguagem natural") String consulta
    ) {
        var resultados = vectorStore.similaritySearch(
            SearchRequest.builder()
                .query(consulta)
                .topK(4)                    // retorna os 4 documentos mais próximos
                .similarityThreshold(0.7)   // mínimo 70% de similaridade
                .build()
        );

        if (resultados.isEmpty()) {
            return "Nenhuma informação encontrada para: " + consulta;
        }

        return resultados.stream()
            .map(doc -> "Fonte: " + doc.getMetadata().getOrDefault("source", "?")
                      + "\nConteúdo: " + doc.getText())
            .collect(Collectors.joining("\n\n---\n\n"));
    }

    @Tool(description = "Retorna a data e hora atual do servidor")
    public String obterDataHoraAtual() {
        return LocalDateTime.now().toString();
    }
}
```

**Como o LLM decide usar uma tool?**
O LLM lê a `description` de cada tool e decide, com base no contexto da conversa, se precisa usar alguma. Por isso a descrição precisa ser clara e específica.

**Exemplo de raciocínio interno do LLM:**
> Pergunta: "Que horas são?"
> Opções: `buscarNaBaseDeConhecimento`, `obterDataHoraAtual`
> Decisão: "A pergunta é sobre horário → uso `obterDataHoraAtual`"

---

## Parte 4 — O Ciclo Agêntico (`AgentService.java`)

Este é o coração do sistema. Vamos entender cada passo.

### O que é o Ciclo Agêntico?

É um loop que repete três fases até o agente ter uma resposta final:

```
┌──────────────────────────────────────────────────────────────────┐
│                    CICLO AGÊNTICO (ReAct)                        │
│                                                                  │
│   Usuário envia: "Quais serviços estão online?"                  │
│                          │                                       │
│                          ▼                                       │
│   ┌─────────────────────────────────────────────┐               │
│   │  RACIOCÍNIO (Reasoning)                     │               │
│   │  LLM lê: histórico + mensagem + tools       │               │
│   │  Decide: "Preciso buscar na base de         │               │
│   │           conhecimento para responder isso"  │               │
│   └──────────────┬──────────────────────────────┘               │
│                  │ pediu tool?                                   │
│          ┌───────┴────────┐                                      │
│          │ SIM            │ NÃO → retorna resposta final         │
│          ▼                                                       │
│   ┌──────────────────────────────────────────────┐              │
│   │  AÇÃO (Action)                               │              │
│   │  Executa: buscarNaBaseDeConhecimento(        │              │
│   │               "serviços online"              │              │
│   │           )                                  │              │
│   └──────────────┬───────────────────────────────┘              │
│                  ▼                                               │
│   ┌──────────────────────────────────────────────┐              │
│   │  OBSERVAÇÃO (Observation)                    │              │
│   │  Resultado: "Serviços: payment, user..."     │              │
│   │  Adiciona ao histórico                       │              │
│   └──────────────┬───────────────────────────────┘              │
│                  │                                               │
│                  └──────► volta para RACIOCÍNIO                  │
└──────────────────────────────────────────────────────────────────┘
```

### O código completo do Service

```java
package TF_ESII.TF.agent;

import TF_ESII.TF.DTO.AgentRequest;
import TF_ESII.TF.DTO.AgentResponse;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AgentService {

    // Limite de segurança: evita loops infinitos
    private static final int MAX_ITERACOES = 10;

    private static final String PROMPT_SISTEMA = """
        Você é um assistente inteligente com acesso a ferramentas.

        Processo obrigatório:
        1. RAZÃO: analise o que o usuário precisa.
        2. AÇÃO: se precisar de dados externos, chame uma ferramenta.
        3. OBSERVAÇÃO: use o resultado da ferramenta para continuar.
        4. Repita até ter informação suficiente para responder.
        5. RESPOSTA FINAL: responda em português de forma clara.

        Não invente informações. Se não souber, diga claramente.
        """;

    private final ChatModel chatModel;
    private final AgentTools agentTools;

    // Guarda o histórico de cada sessão: sessionId → lista de mensagens
    // ConcurrentHashMap porque múltiplas requisições chegam ao mesmo tempo
    private final Map<String, List<Message>> historicoSessoes = new ConcurrentHashMap<>();

    public AgentService(ChatModel chatModel, AgentTools agentTools) {
        this.chatModel = chatModel;
        this.agentTools = agentTools;
    }

    public AgentResponse processar(AgentRequest request) {

        // Passo 1: Recupera ou cria o histórico desta sessão
        List<Message> historico = historicoSessoes.computeIfAbsent(
            request.sessionId(),
            id -> new ArrayList<>(List.of(new SystemMessage(PROMPT_SISTEMA)))
        );

        // Passo 2: Adiciona a mensagem atual do usuário ao histórico
        historico.add(new UserMessage(request.message()));

        List<String> ferramentasUsadas = new ArrayList<>();
        int iteracao = 0;

        // ============================================================
        // CICLO AGÊNTICO PRINCIPAL
        // ============================================================
        while (iteracao < MAX_ITERACOES) {
            iteracao++;

            // ── RACIOCÍNIO ──────────────────────────────────────────
            // Envia TODO o histórico ao LLM junto com as tools disponíveis.
            // O LLM vai ler tudo e decidir o próximo passo.
            ChatResponse respostaLLM = chatModel.call(
                new Prompt(
                    historico,
                    ToolCallingChatOptions.builder()
                        .toolObjects(List.of(agentTools)) // disponibiliza as @Tool
                        .build()
                )
            );

            AssistantMessage mensagem = respostaLLM.getResult().getOutput();

            // Adiciona a resposta do LLM ao histórico (memória da conversa)
            historico.add(mensagem);

            // ── VERIFICAÇÃO: o LLM pediu uma tool ou deu resposta final? ──
            if (!mensagem.hasToolCalls()) {
                // Sem tool calls → ciclo encerrado → esta é a resposta final
                return new AgentResponse(
                    request.sessionId(),
                    mensagem.getText(),
                    ferramentasUsadas,
                    iteracao
                );
            }

            // ── AÇÃO ────────────────────────────────────────────────
            // O LLM pediu uma ou mais ferramentas. Executa cada uma.
            List<ToolResponseMessage.ToolResponse> resultados = new ArrayList<>();

            for (AssistantMessage.ToolCall toolCall : mensagem.getToolCalls()) {
                String nomeDaTool = toolCall.name();
                ferramentasUsadas.add(nomeDaTool);

                // ── OBSERVAÇÃO ──────────────────────────────────────
                // Executa a ferramenta e obtém o resultado (observação)
                String resultado = executarFerramenta(nomeDaTool, toolCall.arguments());

                resultados.add(new ToolResponseMessage.ToolResponse(
                    toolCall.id(),
                    nomeDaTool,
                    resultado
                ));
            }

            // Adiciona os resultados ao histórico.
            // Na próxima iteração, o LLM vai LER esses resultados
            // e usá-los para raciocinar novamente.
            historico.add(new ToolResponseMessage(resultados));

            // → próxima iteração do ciclo (volta para RACIOCÍNIO)
        }

        // Limite atingido sem resposta final
        return new AgentResponse(
            request.sessionId(),
            "Limite de iterações atingido. Tente reformular a pergunta.",
            ferramentasUsadas,
            iteracao
        );
    }

    // Despacha a chamada de tool pelo nome
    private String executarFerramenta(String nome, String argumentosJson) {
        return switch (nome) {
            case "buscarNaBaseDeConhecimento" -> {
                String consulta = extrairParametro(argumentosJson, "consulta");
                yield agentTools.buscarNaBaseDeConhecimento(consulta);
            }
            case "obterDataHoraAtual" -> agentTools.obterDataHoraAtual();
            default -> "Ferramenta desconhecida: " + nome;
        };
    }

    private String extrairParametro(String json, String chave) {
        // Extração simples. Em produção use ObjectMapper do Jackson:
        // objectMapper.readTree(json).get(chave).asText()
        int inicio = json.indexOf("\"" + chave + "\"");
        if (inicio == -1) return json;
        int valorInicio = json.indexOf("\"", inicio + chave.length() + 3) + 1;
        int valorFim = json.indexOf("\"", valorInicio);
        return json.substring(valorInicio, valorFim);
    }
}
```

### Acompanhando o ciclo com um exemplo real

**Usuário envia:** `"Que horas são agora?"`

| Iteração | Fase | O que acontece |
|----------|------|----------------|
| 1 | Raciocínio | LLM lê a pergunta e decide: "Preciso da hora atual → uso `obterDataHoraAtual`" |
| 1 | Ação | O código chama `agentTools.obterDataHoraAtual()` |
| 1 | Observação | Resultado `"2026-06-24T10:30:00"` é adicionado ao histórico |
| 2 | Raciocínio | LLM lê o resultado e decide: "Tenho a informação → posso responder" |
| 2 | — | Sem tool call → **retorna** `"Agora são 10:30 do dia 24/06/2026."` |

**Resultado:** `iterations: 2`, `toolsUsed: ["obterDataHoraAtual"]`

---

## Parte 5 — Os Endpoints (`AgentController.java`)

**O que é um Controller?** É a "porta de entrada" do serviço. Ele recebe requisições HTTP e as encaminha para o Service.

```java
package TF_ESII.TF.agent;

import TF_ESII.TF.DTO.AgentRequest;
import TF_ESII.TF.DTO.AgentResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.Map;

@RestController           // indica que esta classe serve respostas HTTP (JSON)
@RequestMapping("/api/agent")  // prefixo de todas as rotas desta classe
public class AgentController {

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    // ── ENDPOINT PRINCIPAL ─────────────────────────────────────────
    // POST /api/agent/chat
    @PostMapping("/chat")
    public ResponseEntity<AgentResponse> chat(@RequestBody AgentRequest request) {
        AgentResponse resposta = agentService.processar(request);
        return ResponseEntity.ok(resposta);
    }

    // ── ENDPOINT DE NOVA SESSÃO ────────────────────────────────────
    // POST /api/agent/nova-sessao
    // Útil para começar uma conversa nova sem informar sessionId
    @PostMapping("/nova-sessao")
    public ResponseEntity<AgentResponse> novaSessao(@RequestBody Map<String, String> body) {
        var request = new AgentRequest(body.get("message"), null); // null → gera novo sessionId
        return ResponseEntity.ok(agentService.processar(request));
    }

    // ── HEALTH CHECK ───────────────────────────────────────────────
    // GET /api/agent/health
    // Usado pelo Eureka e pelo Circuit Breaker para saber se o serviço está vivo
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "agent-service",
            "timestamp", LocalDateTime.now().toString()
        ));
    }
}
```

**Testando com curl:**

```bash
# Enviar uma mensagem
curl -X POST http://localhost:8090/api/agent/chat \
     -H "Content-Type: application/json" \
     -d '{"message": "Que horas são?", "sessionId": "teste-001"}'

# Iniciar sessão nova
curl -X POST http://localhost:8090/api/agent/nova-sessao \
     -H "Content-Type: application/json" \
     -d '{"message": "Olá, pode me ajudar?"}'

# Verificar saúde do serviço
curl http://localhost:8090/api/agent/health
```

---

## Parte 6 — Configuração (`application.yaml`)

Crie um `application.yaml` **no agent-service** (não no naming-server):

```yaml
server:
  port: 8090

spring:
  application:
    name: agent-service   # nome que aparece no Eureka

  ai:
    ollama:
      base-url: http://localhost:11434
      chat:
        options:
          model: llama3.2       # modelo instalado localmente
          temperature: 0.3      # 0 = mais determinístico, 1 = mais criativo
          num-ctx: 8192         # tamanho máximo do contexto (tokens)

    vectorstore:
      qdrant:
        host: localhost
        port: 6334
        collection-name: agent-knowledge
        use-tls: false

eureka:
  client:
    service-url:
      defaultZone: http://localhost:8765/eureka   # aponta para o naming-server
  instance:
    prefer-ip-address: true
```

---

## Parte 7 — A Classe Principal do Microserviço

```java
package TF_ESII.TF;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient  // registra no Eureka automaticamente ao iniciar
public class AgentServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AgentServiceApplication.class, args);
    }
}
```

---

## Parte 8 — Fluxo Completo de Uma Requisição

Acompanhe o caminho de uma mensagem do começo ao fim:

```
[Usuário]
    │
    │  POST /api/agent/chat
    │  Body: {"message": "Busque info sobre o sistema", "sessionId": "s1"}
    ▼
[AgentController.chat()]
    │  Recebe AgentRequest
    │  Chama agentService.processar(request)
    ▼
[AgentService.processar()]
    │
    │  1. Recupera/cria histórico da sessão "s1"
    │  2. Adiciona UserMessage ao histórico
    │
    │  ┌── ITERAÇÃO 1 ─────────────────────────────────────────┐
    │  │                                                        │
    │  │  RACIOCÍNIO: envia histórico ao Ollama                │
    │  │  Ollama responde: "vou usar buscarNaBaseDeConhecimento"│
    │  │                                                        │
    │  │  AÇÃO: chama agentTools.buscarNaBaseDeConhecimento(   │
    │  │            "informações sobre o sistema"               │
    │  │        )                                               │
    │  │        → Qdrant retorna documentos similares          │
    │  │                                                        │
    │  │  OBSERVAÇÃO: adiciona resultado ao histórico           │
    │  └────────────────────────────────────────────────────────┘
    │
    │  ┌── ITERAÇÃO 2 ─────────────────────────────────────────┐
    │  │                                                        │
    │  │  RACIOCÍNIO: Ollama lê o resultado da busca           │
    │  │  Ollama decide: "tenho a informação → vou responder"  │
    │  │  Sem tool call → fim do ciclo                         │
    │  └────────────────────────────────────────────────────────┘
    │
    │  Retorna AgentResponse:
    │    sessionId: "s1"
    │    answer: "O sistema possui..."
    │    toolsUsed: ["buscarNaBaseDeConhecimento"]
    │    iterations: 2
    ▼
[AgentController]
    │  ResponseEntity.ok(resposta)
    ▼
[Usuário recebe JSON de resposta]
```

---

## Roteiro de Implementação

Siga esta ordem para não se perder:

- [ ] **Passo 1** — Corrija `AgentRequest.java` (o `record` está incompleto no arquivo atual)
- [ ] **Passo 2** — Corrija `AgentResponse.java` (o `record` está aninhado desnecessariamente)
- [ ] **Passo 3** — Corrija `AgentConfig.java` (use `@Configuration` no lugar de `@Configurable`)
- [ ] **Passo 4** — Crie `AgentTools.java` com os dois métodos `@Tool`
- [ ] **Passo 5** — Crie `AgentService.java` com o ciclo agêntico completo
- [ ] **Passo 6** — Crie `AgentController.java` com os três endpoints
- [ ] **Passo 7** — Crie o `application.yaml` do agent-service com as configurações do Ollama e Qdrant
- [ ] **Passo 8** — Adicione `@EnableDiscoveryClient` na classe principal
- [ ] **Passo 9** — Suba o Ollama localmente e instale o modelo (`ollama pull llama3.2`)
- [ ] **Passo 10** — Teste com `curl` ou Postman

---

## Erros Comuns e Como Corrigir

| Erro | Causa provável | Solução |
|------|----------------|---------|
| `AgentRequest` não compila | `record` aninhado dentro de uma `class` sem ser `static` | Torne o record um arquivo próprio |
| `@Configurable` não funciona | É para AspectJ, não para beans normais | Use `@Configuration` |
| Ollama não responde | Serviço não iniciado | Rode `ollama serve` no terminal |
| Qdrant connection refused | Qdrant não está rodando | Use Docker: `docker run -p 6334:6334 qdrant/qdrant` |
| Loop infinito no ciclo | Tool sempre chamada sem resultado útil | Verifique `MAX_ITERACOES` e a lógica das tools |
| Sessão não persiste entre reinicializações | `ConcurrentHashMap` é volátil | Em produção, use Redis com `spring-session-data-redis` |

---

## Conceitos-chave para revisar

- **`record` no Java 21**: classe imutável e concisa para transportar dados. Os campos são definidos no construtor e gerados automaticamente com getters, `equals`, `hashCode` e `toString`.
- **`@Bean`**: avisa o Spring para criar e gerenciar aquele objeto. Qualquer classe que precisar dele pode receber via injeção de dependência.
- **`@Tool`**: anotação do Spring AI que expõe um método Java ao LLM como uma ação disponível.
- **`ConcurrentHashMap`**: mapa thread-safe, necessário porque múltiplas requisições HTTP chegam em paralelo.
- **`ToolCallingChatOptions`**: opções passadas ao LLM junto com a prompt, informando quais tools estão disponíveis nesta chamada.
- **`computeIfAbsent`**: obtém o valor do mapa se a chave existe; senão, cria e insere. Evita condição de corrida na criação do histórico.