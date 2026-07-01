# Como Executar — Plataforma de Agentes Conversacionais

Este guia cobre a containerização da plataforma com Docker Compose, os testes de cada serviço e os resultados esperados.

---

## Pré-requisitos

| Ferramenta | Versão mínima | Verificar |
|---|---|---|
| Docker Desktop | 24+ | `docker --version` |
| Docker Compose | 2.20+ | `docker compose version` |
| Git | qualquer | `git --version` |

> **Memória RAM:** recomendado pelo menos 8 GB livres para rodar Ollama + todos os serviços simultaneamente.

---

## 1. Baixar o modelo de linguagem (obrigatório antes do primeiro start)

O Ollama precisa ter o modelo disponível localmente. Execute **uma vez** antes de subir os containers:

```bash
# Puxa o modelo llama3.2 (≈2 GB de download)
docker run --rm -v ollama_data:/root/.ollama ollama/ollama pull llama3.2
```

> Se preferir outro modelo (ex: `llama3.2:1b` para máquinas mais modestas), ajuste a variável `LLM_MODEL` no `compose.yaml`.

---

## 2. Subir toda a plataforma

Na raiz do projeto (onde está o `compose.yaml`):

```bash
# Build de todas as imagens e start dos containers
docker compose up --build
```

Para rodar em background (detached):

```bash
docker compose up --build -d
```

### Ordem de inicialização esperada

```
naming-server   →  rabbitmq + ollama  →  llm-gateway  →  agent-service
```

O `depends_on` com `condition: service_healthy` garante esta ordem automaticamente. Na primeira vez, aguarde cerca de **2–3 minutos** até todos os serviços estarem saudáveis.

---

## 3. Verificar status dos serviços

```bash
docker compose ps
```

Todos devem mostrar status `running` (healthy):

| Serviço | Porta | Health endpoint |
|---|---|---|
| naming-server | 8761 | http://localhost:8761/actuator/health |
| ollama | 11434 | http://localhost:11434/api/tags |
| llm-gateway | 8767 | http://localhost:8767/health |
| agent-service | 8765 | http://localhost:8765/actuator/health |
| rabbitmq | 15672 | http://localhost:15672 (guest/guest) |

---

## 4. Testes

### 4.1 Testar o naming-server (Eureka)

```bash
curl http://localhost:8761/eureka/apps
```

**Resultado esperado:** XML ou JSON listando os serviços registrados. Após todos subirem, você verá `llm-gateway` e `agent-service` na lista.

Acesse também o painel web: [http://localhost:8761](http://localhost:8761)

---

### 4.2 Testar o llm-gateway diretamente

**Health check:**
```bash
curl http://localhost:8767/health
```

Resultado esperado:
```json
{
  "status": "UP",
  "service": "llm-gateway",
  "model": "ollama/llama3.2",
  "ollamaBase": "http://ollama:11434"
}
```

**Chamada de chat:**
```bash
curl -X POST http://localhost:8767/api/llm/chat \
  -H "Content-Type: application/json" \
  -d '{
    "messages": [
      {"role": "system", "content": "Você é um assistente direto e objetivo."},
      {"role": "user",   "content": "Qual é a capital do Brasil?"}
    ],
    "availableTools": []
  }'
```

Resultado esperado:
```json
{
  "content": "A capital do Brasil é Brasília.",
  "toolCalls": [],
  "finishReason": "stop"
}
```

> Na primeira chamada, o Ollama pode demorar 10–30 segundos para carregar o modelo em memória.

---

### 4.3 Testar o agent-service

**Health check:**
```bash
curl http://localhost:8765/actuator/health
```

**Enviar uma mensagem ao agente (ciclo ReAct completo):**
```bash
curl -X POST http://localhost:8765/api/agent/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Qual é a data e hora atual?",
    "sessionId": null
  }'
```

Resultado esperado (exemplo):
```json
{
  "sessionId": "a1b2c3d4-...",
  "answer": "A data e hora atual é 2026-06-30T14:35:22.",
  "toolsUsed": ["obterDataHoraAtual"],
  "iterations": 2
}
```

> O campo `toolsUsed` mostra quais ferramentas o agente invocou. `iterations` indica quantos ciclos raciocínio→ação o agente executou.

**Manter sessão (memória de conversa):**
```bash
# Copie o sessionId da resposta anterior e reutilize
curl -X POST http://localhost:8765/api/agent/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "E qual foi minha pergunta anterior?",
    "sessionId": "a1b2c3d4-..."
  }'
```

---

### 4.4 Verificar telemetria assíncrona no RabbitMQ

Acesse o painel do RabbitMQ: [http://localhost:15672](http://localhost:15672) (login: `guest` / `guest`)

Após enviar mensagens ao agent-service, navegue em **Queues → agent.telemetry** e verifique as mensagens recebidas. Cada chamada ao LLM publica uma mensagem com:

```json
{
  "sessionId": "...",
  "iteracao": 1,
  "duracaoMs": 1250,
  "finishReason": "stop",
  "timestamp": "2026-06-30T14:35:22Z"
}
```

---

## 5. Parar os containers

```bash
# Para e remove containers (preserva volumes)
docker compose down

# Para, remove containers E volumes (limpa modelo Ollama baixado)
docker compose down -v
```

---

## 6. Troubleshooting

### llm-gateway demora para responder na primeira chamada
Normal — o Ollama precisa carregar o modelo em VRAM/RAM. Aguarde até 60 segundos na primeira requisição.

### `service_healthy` nunca passa para o ollama
O healthcheck do Ollama usa `ollama list`. Se o container não tiver o modelo baixado, pode retornar erro. Execute o passo 1 novamente.

### agent-service não encontra o llm-gateway
Verifique se o `llm-gateway` está registrado no Eureka em [http://localhost:8761](http://localhost:8761). O Feign Client do agent-service usa service discovery — se o llm-gateway não aparecer no Eureka, a chamada falha.

### Ver logs de um serviço específico
```bash
docker compose logs -f llm-gateway
docker compose logs -f agent-service
docker compose logs -f naming-server
```

---

## 7. Resumo da comunicação entre serviços

```
curl (usuário)
    │  POST /api/agent/chat
    ▼
agent-service (8765)
    │  Feign Client → Eureka descobre "llm-gateway"
    │  POST /api/llm/chat
    ▼
llm-gateway (8767)
    │  LiteLLM → Ollama API
    │  POST http://ollama:11434/api/chat
    ▼
ollama (11434) → llama3.2
    │  resposta LLM
    ▲
llm-gateway → LlmChatResponse
    ▲
agent-service → ciclo ReAct → AgentResponse
    ▲
curl (usuário) recebe resposta final
```
