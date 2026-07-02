# Como Executar — Plataforma de Agentes Conversacionais

---

## Estrutura de pastas (importante saber antes de começar)

```
trabalhoESII/              ← RAIZ DO PROJETO — rode o compose principal aqui
├── compose.yaml           ← arquivo principal que sobe TUDO junto
├── COMO_EXECUTAR.md
├── naming-server/         ← Eureka (servidor de nomes)
│   ├── Dockerfile
│   └── docker-compose.yml ← serve só para testar o naming-server isolado
├── llm-gateway/           ← Gateway Python que acessa o Ollama
│   ├── Dockerfile
│   └── main.py
└── agent-service/         ← Serviço Java com o ciclo agêntico
    ├── Dockerfile
    └── src/
```

> **Regra de ouro:** use sempre o `compose.yaml` da **raiz** (`trabalhoESII/`) para subir a plataforma completa. Os `Dockerfile` dentro de cada pasta são usados automaticamente por ele.

---

## Pré-requisitos

| Ferramenta | Versão mínima | Como verificar |
|---|---|---|
| Docker Desktop | 24+ | `docker --version` |
| Docker Compose | 2.20+ | `docker compose version` |

> **RAM recomendada:** mínimo 8 GB livres para rodar Ollama + todos os serviços.

---

## Passo 1 — Subir toda a plataforma

> **Pasta obrigatória:** `trabalhoESII/` (onde está o `compose.yaml`)

O modelo `qwen3.5` é baixado automaticamente pelo serviço `ollama-pull` na primeira execução — não é necessário nenhum passo manual antes.

```powershell
# Constrói as imagens e sobe todos os containers
docker compose up --build
```

Para rodar em background (libera o terminal):
```powershell
docker compose up --build -d
```

> **Na primeira execução**, o `ollama-pull` vai baixar o modelo (~2 GB). Acompanhe o progresso com:
> ```powershell
> docker compose logs -f ollama-pull
> ```

### Ordem de inicialização automática

O `compose.yaml` garante esta sequência via `depends_on`:

```
naming-server (8761)
      │
      ├── rabbitmq (5672)
      │
      └── ollama (11434)
              │
          ollama-pull  ← baixa qwen3.5 automaticamente (só na 1ª vez)
              │
          llm-gateway (8767)
                  │
            agent-service (8765)
```

Na primeira execução, aguarde **5–10 minutos** (inclui o download do modelo). Nas próximas, **2–3 minutos**.

---

## Passo 3 — Confirmar que tudo subiu

```powershell
# Ainda na pasta trabalhoESII/
docker compose ps
```

Todos devem mostrar `running`:

| Serviço | Porta | URL de verificação |
|---|---|---|
| naming-server | 8761 | http://localhost:8761 |
| rabbitmq | 15672 | http://localhost:15672 (guest/guest) |
| ollama | 11434 | http://localhost:11434/api/tags |
| llm-gateway | 8767 | http://localhost:8767/health |
| agent-service | 8765 | http://localhost:8765/actuator/health |

---

## Passo 4 — Testes

### 4.1 Naming-server (Eureka)

Abra no browser: [http://localhost:8761](http://localhost:8761)

Você deve ver o painel do Eureka com `llm-gateway` e `agent-service` registrados.

---

### 4.2 LLM-gateway

**Health check:**
```powershell
curl http://localhost:8767/health
```
Resultado esperado:
```json
{"status": "UP", "service": "llm-gateway", "model": "qwen3.5", "ollamaBase": "http://ollama:11434"}
```

**Teste de chat direto (sem passar pelo agent-service):**
```powershell
curl -X POST http://localhost:8767/api/llm/chat `
  -H "Content-Type: application/json" `
  -d '{\"messages\": [{\"role\": \"user\", \"content\": \"Qual a capital do Brasil?\"}]}'
```
Resultado esperado:
```json
{"content": "A capital do Brasil é Brasília.", "toolCalls": [], "finishReason": "stop"}
```

> Na **primeira chamada** o Ollama pode demorar 10–60 segundos para carregar o modelo em memória. As próximas são rápidas.

---

### 4.3 Agent-service (ciclo ReAct completo)

**Health check:**
```powershell
curl http://localhost:8765/actuator/health
```

**Enviar mensagem ao agente:**
```powershell
curl -X POST http://localhost:8765/api/agent/chat `
  -H "Content-Type: application/json" `
  -d '{\"message\": \"Qual a data e hora atual?\", \"sessionId\": null}'
```
Resultado esperado:
```json
{
  "sessionId": "a1b2c3d4-...",
  "answer": "A data e hora atual é 2026-06-30T14:35:22.",
  "toolsUsed": ["obterDataHoraAtual"],
  "iterations": 2
}
```

**Continuar a mesma sessão (memória de conversa):**
```powershell
curl -X POST http://localhost:8765/api/agent/chat `
  -H "Content-Type: application/json" `
  -d '{\"message\": \"E qual foi minha pergunta anterior?\", \"sessionId\": \"a1b2c3d4-...\"}'
```

---

### 4.4 Telemetria no RabbitMQ

Acesse [http://localhost:15672](http://localhost:15672) (login: `guest` / `guest`).

Navegue em **Queues → agent.telemetry**. Após enviar mensagens ao agent-service, você verá mensagens como:
```json
{"sessionId": "...", "iteracao": 1, "duracaoMs": 1250, "finishReason": "stop", "timestamp": "..."}
```

---

## Parar os containers

```powershell
# Na pasta trabalhoESII/
# Para e remove os containers (mantém o volume com o modelo Ollama)
docker compose down

# Para, remove containers E o modelo baixado (precisa baixar de novo)
docker compose down -v
```

---

## Troubleshooting

### `ollama` nunca fica healthy / `ollama-pull` falha
O download do modelo falhou. Force o re-download com:
```powershell
docker compose up ollama -d
docker compose logs -f ollama-pull
```
Se o pull falhar por nome de modelo incorreto, verifique o nome em [https://ollama.com/library](https://ollama.com/library) e atualize `LLM_MODEL` no `compose.yaml`.

### `llm-gateway` falha no build: `No matching distribution found for py-eureka-client==0.11.14`
Versão inexistente no PyPI. Edite `llm-gateway/requirements.txt` e troque por `py-eureka-client==0.11.13`. *(Já corrigido se você atualizou o repositório.)*

### agent-service não encontra o llm-gateway
Verifique o Eureka em [http://localhost:8761](http://localhost:8761). O `llm-gateway` precisa aparecer na lista de serviços registrados. Se não aparecer, veja os logs:
```powershell
docker compose logs -f llm-gateway
```

### Ver logs de um serviço específico
```powershell
# Todos os logs ao vivo
docker compose logs -f

# Logs de um serviço específico
docker compose logs -f naming-server
docker compose logs -f llm-gateway
docker compose logs -f agent-service
```

### Erro de porta já em uso
Algum processo já está usando a porta. Identifique e encerre:
```powershell
netstat -ano | findstr :8761
netstat -ano | findstr :8767
netstat -ano | findstr :8765
```

---

## Resumo do fluxo de comunicação

```
Você (curl)
    │  POST /api/agent/chat
    ▼
agent-service :8765
    │  Feign → descobre "llm-gateway" via Eureka (:8761)
    │  POST /api/llm/chat
    ▼
llm-gateway :8767
    │  LiteLLM → Ollama API
    │  POST http://ollama:11434/api/chat
    ▼
ollama :11434  →  qwen3.5
    │
    └─ resposta sobe pela cadeia até você
```
