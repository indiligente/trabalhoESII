# Alterações aplicadas ao Agent Service

## Visão geral

Este documento descreve todas as alterações feitas a partir do arquivo de referência `EXCLUIRDEPOIS.java`, seguindo as regras definidas pelo professor: manter a estrutura do ciclo agêntico (Abordagem B), substituir dependências diretas de IA por chamadas OpenFeign aos microserviços dos colegas, e adicionar comunicação assíncrona com RabbitMQ.

---

## 1. DTOs corrigidos (`DTO/`)

### `AgentRequest.java`
**O que estava errado:** o `record` estava aninhado dentro de uma `class` com sintaxe inválida (`AgentRequest{}` como construtor compacto fora de um record). Os nomes dos campos eram inconsistentes (`idSessao` vs `sessionId`).

**O que foi feito:** reescrito como um `record` de nível superior com campos `message` e `sessionId`. O construtor compacto gera um `UUID` automaticamente se `sessionId` for nulo ou vazio, exatamente como no Bloco 1 do arquivo de referência.

---

### `AgentResponse.java`
**O que estava errado:** mesmo problema — `record` aninhado em `class`, com nomes errados (`answare`, `ferramenta`, `iteracoes`).

**O que foi feito:** reescrito como `record` de nível superior com os campos corretos: `sessionId`, `answer`, `toolsUsed`, `iterations`.

---

## 2. Configuração corrigida (`config/AgentConfig.java`)

**O que estava errado:** anotação `@Configurable` (usada para AspectJ weaving, completamente diferente) no lugar de `@Configuration`. O método `chatclient()` tentava criar um `ChatClient` bean para acesso direto ao Ollama.

**O que foi feito:** corrigido para `@Configuration`. O bean `ChatClient` foi removido porque esta arquitetura **não acessa o Ollama diretamente** — as chamadas ao LLM vão via OpenFeign para o `llm-gateway` do colega. Manter o bean causaria falha na inicialização se o Ollama não estivesse rodando.

---

## 3. DTOs de comunicação LLM (novos — `DTO/llm/`)

Criados quatro records para tipificar a comunicação com o `llm-gateway`:

| Arquivo | Propósito |
|---|---|
| `LlmMessage.java` | Uma mensagem no histórico: `role` ("system", "user", "assistant", "tool") + `content` |
| `LlmChatRequest.java` | Corpo da requisição ao gateway: lista de mensagens + lista de tools disponíveis |
| `LlmChatResponse.java` | Resposta do gateway: `content` (texto final), `toolCalls` (lista de ações) e `finishReason` |
| `ToolCall.java` | Uma chamada de ferramenta: `id`, `name` e `arguments` (JSON) |

**Motivo:** o `AgentService` precisa de tipos próprios para serializar/desserializar a comunicação via Feign. Sem isso, seria necessário trabalhar com `Map<String, Object>` cru em todo o código, o que é frágil.

---

## 4. Feign Clients (novos — `feign/`)

Três interfaces `@FeignClient` que substituem as dependências diretas de IA:

### `LlmGatewayClient`
**Substitui:** injeção de `ChatModel` (Ollama direto).  
**O que faz:** `POST /api/llm/chat` → envia o histórico de mensagens e recebe a resposta do LLM (texto final ou tool calls). O `llm-gateway` é um microserviço criado pelo colega que encapsula a comunicação com o Ollama.

### `RetrievalServiceClient`
**Substitui:** injeção de `VectorStore` (Qdrant direto).  
**O que faz:** `POST /api/retrieval/search` → envia uma query e recebe os documentos mais relevantes da base de conhecimento. O `retrieval-service` é o microserviço que gerencia o Qdrant.

### `MemoryServiceClient`
**Substitui:** `InMemoryChatMemory` (histórico em RAM, perdido ao reiniciar).  
**O que faz:**  
- `GET /api/memory/{sessionId}` → carrega o histórico persistido da sessão.  
- `POST /api/memory/{sessionId}` → salva cada nova mensagem.  
O `memory-service` é o microserviço do colega que persiste o histórico (ex: Redis ou banco).

**Todos os três clientes usam o nome do serviço no Eureka** (`name = "llm-gateway"` etc.), portanto a descoberta de serviço via Eureka resolve os endereços automaticamente — exatamente o padrão aprendido no Roteiro 2.

---

## 5. `AgentTools.java` (novo — `agent/`)

**O que foi feito:** criado com as duas ferramentas do arquivo de referência, mas adaptadas:

- `buscarNaBaseDeConhecimento(String consulta)` → chama `RetrievalServiceClient.search()` via Feign em vez de `vectorStore.similaritySearch()` direto.
- `obterDataHoraAtual()` → sem mudanças, retorna `LocalDateTime.now()`.

A anotação `@Tool` do Spring AI foi **removida** porque o gerenciamento das tools agora é manual (Abordagem B): o `AgentService` decide quando e como chamar cada ferramenta pelo nome.

---

## 6. `AgentService.java` (reescrito — `agent/`)

**O que estava errado:** o método `processar()` estava incompleto — loop sem corpo, ponto-e-vírgula faltando, tipos não importados, classe `AgentTools` não existia.

**O que foi feito:** implementação completa da **Abordagem B (ciclo ReAct manual)** com todas as adaptações necessárias:

### Ciclo agêntico (Reasoning → Action → Observation)
Cada iteração do `while`:
1. **REASONING** — envia o histórico completo ao `llm-gateway` via `LlmGatewayClient.chat()`.
2. Se a resposta não tiver `toolCalls` → **resposta final**, encerra o ciclo.
3. **ACTION** — para cada tool call retornada pelo LLM, chama `executarFerramenta()`.
4. **OBSERVATION** — adiciona o resultado da tool ao histórico como mensagem `"tool"`.
5. Repete até resposta final ou `MAX_ITERACOES = 10`.

### Memória de sessão via Feign
- No início de cada requisição: carrega o histórico salvo do `memory-service`.
- Após cada mensagem nova (usuário, assistente, tool): salva via `memory-service`.
- Ambas as chamadas têm `try/catch` → se o `memory-service` estiver indisponível, o fluxo continua com histórico local (resiliência).

### RabbitMQ — telemetria assíncrona *(requisito adicionado)*
Após cada chamada ao LLM (`publicarTelemetria()`), publica na fila `agent.telemetry`:
```json
{
  "sessionId": "...",
  "iteracao": 2,
  "duracaoMs": 1350,
  "finishReason": "TOOL_CALLS",
  "timestamp": "2026-06-28T..."
}
```
O `try/catch` garante que se o RabbitMQ estiver indisponível, o agente não é bloqueado.

---

## 7. `AgentController.java` (corrigido — `agent/`)

**O que estava errado:** faltava `@RestController`, faltavam imports de `ResponseEntity`, `@RequestBody`, `@PostMapping`, `@GetMapping`. O endpoint de nova sessão chamava `/newsession` (sem padrão).

**O que foi feito:** adicionadas todas as anotações e imports. Endpoint renomeado de `/newsession` para `/nova-sessao` para ficar consistente com o padrão do projeto.

**Endpoints disponíveis:**
| Método | Path | Descrição |
|---|---|---|
| `POST` | `/api/agent/chat` | Envia mensagem; mantém sessão pelo `sessionId` no body |
| `POST` | `/api/agent/nova-sessao` | Cria sessão nova automaticamente |
| `GET` | `/api/agent/health` | Health check do serviço |

---

## 8. `TfApplication.java` (atualizado)

Adicionada a anotação `@EnableFeignClients` para habilitar a descoberta e criação dos beans `LlmGatewayClient`, `RetrievalServiceClient` e `MemoryServiceClient` pelo Spring.

---

## 9. `pom.xml` (atualizado)

Duas dependências adicionadas:

```xml
<!-- Comunicação assíncrona via RabbitMQ -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>

<!-- OpenFeign para chamar os microserviços dos colegas -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-openfeign</artifactId>
</dependency>
```

**Motivo do AMQP:** requisito explícito do trabalho — o agent-service deve publicar métricas de telemetria de forma assíncrona em uma fila, sem bloquear o ciclo agêntico.

**Motivo do OpenFeign:** substitui o acesso direto ao Ollama e ao Qdrant por chamadas HTTP aos microserviços dos colegas, seguindo o padrão de comunicação entre microserviços do Roteiro 2.

---

## 10. `application.yaml` (atualizado)

Adicionadas duas seções:

```yaml
spring:
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest

feign:
  client:
    config:
      default:
        connectTimeout: 5000   # 5s para estabelecer conexão
        readTimeout: 30000     # 30s para aguardar resposta do LLM
```

O `readTimeout` de 30 segundos é necessário porque chamadas ao LLM podem ser lentas dependendo do modelo e da carga do `llm-gateway`.

---

## Resumo das adaptações por bloco

| Bloco do EXCLUIRDEPOIS | Status | O que mudou |
|---|---|---|
| Bloco 1 — DTOs | Corrigido | Records reescritos corretamente como tipos de nível superior |
| Bloco 2 — Config | Simplificado | `@Configuration` corrigido; bean `ChatClient` removido |
| Bloco 3 — Tools | Adaptado | `VectorStore` → `RetrievalServiceClient` (Feign) |
| Bloco 4 — AgentService | Completado + Adaptado | `ChatModel` → `LlmGatewayClient`; `InMemoryChatMemory` → `MemoryServiceClient`; RabbitMQ adicionado |
| Bloco 5 — Controller | Corrigido | `@RestController` e imports faltantes adicionados |
| Bloco 6 — application.yaml | Atualizado | RabbitMQ e timeouts Feign adicionados |

## Arquivos que NÃO devem mais ser usados

- `EXCLUIRDEPOIS.java` — pode ser excluído após validar que o projeto compila.
- `agent/application.yaml` — arquivo no lugar errado (dentro de `src/main/java/`). Spring Boot não lê configurações desse caminho; o yaml correto fica em `src/main/resources/application.yaml`.
