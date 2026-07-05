# Como Executar — Plataforma de Agentes Conversacionais

---

## Estrutura de pastas (importante saber antes de começar)

```
trabalhoESII/              ← RAIZ DO PROJETO — rode o compose principal aqui
├── compose.yaml           ← arquivo principal que sobe TUDO junto
├── COMO_EXECUTAR.md
├── INTEGRACAO_AGENT_RETRIEVAL.md  ← ajustes feitos para ligar agent-service ao retrieval-service
├── naming-server/         ← Eureka (servidor de nomes)
│   ├── Dockerfile
│   └── docker-compose.yml ← serve só para testar o naming-server isolado
├── llm-gateway/           ← Gateway Python que acessa o Ollama
│   ├── Dockerfile
│   └── main.py
├── agent-service/         ← Serviço Java com o ciclo agêntico
│   ├── Dockerfile
│   └── src/
└── retrieval-service/     ← Serviço Java de busca semântica (RAG) com Qdrant
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

### Alternativa: subir só agent-service + retrieval-service (sem LLM)

Se você só quer validar a comunicação `agent-service` ↔ `retrieval-service`
(sem baixar o modelo de chat `qwen3.5` nem subir o `llm-gateway`), use o
arquivo `compose.retrieval-test.yaml`, que traz uma stack reduzida com
`naming-server`, `rabbitmq`, `qdrant`, `ollama` (só para o modelo de
embedding), `retrieval-service` e `agent-service`:

```powershell
docker compose -f compose.retrieval-test.yaml up --build
```

Use o endpoint `GET /api/agent/debug/retrieval?query=...` do agent-service
para acionar a busca no retrieval-service diretamente, sem passar pelo ciclo
ReAct/LLM (veja a seção 4.3 abaixo). Essa stack reduzida foi testada e
validada como parte da integração — veja
[`INTEGRACAO_AGENT_RETRIEVAL.md`](INTEGRACAO_AGENT_RETRIEVAL.md).

### Ordem de inicialização automática

O `compose.yaml` garante esta sequência via `depends_on`:

```
naming-server (8761)
      │
      ├── rabbitmq (5672)
      │
      ├── qdrant (6333/6334)
      │
      └── ollama (11434)
              │
              ├── ollama-pull        ← baixa qwen3.5 (chat, só na 1ª vez)
              │        │
              │   llm-gateway (8767)
              │
              └── ollama-pull-embed  ← baixa nomic-embed-text (embedding, só na 1ª vez)
                       │
                  retrieval-service (8766)
                          │
                    agent-service (8765)  ← depende de llm-gateway E retrieval-service
```

Na primeira execução, aguarde **5–10 minutos** (inclui o download dos dois modelos). Nas próximas, **2–3 minutos**.

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
| qdrant | 6333 | http://localhost:6333/readyz |
| ollama | 11434 | http://localhost:11434/api/tags |
| llm-gateway | 8767 | http://localhost:8767/health |
| retrieval-service | 8766 | http://localhost:8766/actuator/health |
| agent-service | 8765 | http://localhost:8765/actuator/health |

---

## Passo 4 — Testes

### 4.1 Naming-server (Eureka)

Abra no browser: [http://localhost:8761](http://localhost:8761)

Você deve ver o painel do Eureka com `llm-gateway`, `retrieval-service` e `agent-service` registrados.

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

### 4.3 Retrieval-service (RAG — busca semântica)

**Health check:**
```powershell
curl http://localhost:8766/actuator/health
```

**1. Indexar um documento** (grava no Qdrant, gerando o embedding via Ollama):
```powershell
curl -X POST http://localhost:8766/api/retrieval/documents `
  -H "Content-Type: application/json" `
  -d '{\"content\": \"A capital do Brasil é Brasília, fundada em 21 de abril de 1960.\", \"metadata\": {\"origem\": \"teste\"}}'
```
Resultado esperado:
```json
{"status": "indexed"}
```

> Na **primeira chamada**, o Ollama pode demorar para carregar o modelo de embedding (`nomic-embed-text`) em memória.
>
> Se der `400 Bad Request` com `Invalid UTF-8 middle byte` no lugar do `{"status": "indexed"}`,
> o terminal corrompeu os acentos ao montar o JSON inline. Salve o corpo num
> arquivo `documento.json` (em UTF-8) e use `curl --data-binary @documento.json` no lugar do `-d '...'`.

**2. Buscar por similaridade** (diretamente no retrieval-service, sem passar pelo agent-service):
```powershell
curl -X POST http://localhost:8766/api/retrieval/search `
  -H "Content-Type: application/json" `
  -d '{\"query\": \"Qual a capital do Brasil?\", \"topK\": 3}'
```
Resultado esperado (o documento indexado no passo 1 deve aparecer entre os resultados, com `score` alto):
```json
{
  "results": [
    {
      "id": "...",
      "content": "A capital do Brasil é Brasília, fundada em 21 de abril de 1960.",
      "score": 0.85,
      "metadata": {"origem": "teste"}
    }
  ]
}
```
Se `results` vier vazio, verifique se o documento foi indexado (passo 1) e se `qdrant`/`ollama-pull-embed` estão saudáveis (`docker compose ps`).

**3. Buscar através do agent-service, sem o LLM** — exercita o caminho real
`agent-service → Eureka → Feign → retrieval-service`, sem depender do
`llm-gateway` estar no ar:
```powershell
curl -G "http://localhost:8765/api/agent/debug/retrieval" --data-urlencode "query=Qual a capital do Brasil?"
```
Resultado esperado (mesmo documento do passo 1, agora formatado pelo `AgentTools`):
```json
{
  "query": "Qual a capital do Brasil?",
  "resultado": "- A capital do Brasil é Brasília, fundada em 21 de abril de 1960. (score=0.84...)"
}
```
Se vier `"resultado": "Base de conhecimento indisponível no momento."`, o
Feign não conseguiu falar com o `retrieval-service` — confira o Eureka (4.1)
e os logs (`docker compose logs -f retrieval-service`).

> Essa é a forma recomendada de testar a integração isoladamente: use a stack
> reduzida `compose.retrieval-test.yaml` (ver Passo 1) com os passos 1–3
> acima. Ela não depende do `llm-gateway`/modelo de chat.

---

### 4.4 Agent-service (ciclo ReAct completo)

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

**Testar a integração com o retrieval-service (RAG)** — use a mesma pergunta indexada na seção 4.3:
```powershell
curl -X POST http://localhost:8765/api/agent/chat `
  -H "Content-Type: application/json" `
  -d '{\"message\": \"Segundo a base de conhecimento, qual a capital do Brasil e quando foi fundada?\", \"sessionId\": null}'
```
Resultado esperado — `toolsUsed` deve conter `buscarNaBaseDeConhecimento` e a resposta deve citar Brasília/1960 (o dado que só existe no documento indexado, não no conhecimento do modelo):
```json
{
  "sessionId": "...",
  "answer": "De acordo com a base de conhecimento, a capital do Brasil é Brasília, fundada em 21 de abril de 1960.",
  "toolsUsed": ["buscarNaBaseDeConhecimento"],
  "iterations": 2
}
```
Se `toolsUsed` não incluir `buscarNaBaseDeConhecimento` ou a resposta não citar a data de fundação, verifique se o documento foi indexado (4.3) e se `retrieval-service` aparece saudável no Eureka (4.1).

**Continuar a mesma sessão (memória de conversa):**
```powershell
curl -X POST http://localhost:8765/api/agent/chat `
  -H "Content-Type: application/json" `
  -d '{\"message\": \"E qual foi minha pergunta anterior?\", \"sessionId\": \"a1b2c3d4-...\"}'
```

---

### 4.5 Telemetria no RabbitMQ

Acesse [http://localhost:15672](http://localhost:15672) (login: `guest` / `guest`).

Navegue em **Queues → agent.telemetry**. Após enviar mensagens ao agent-service, você verá mensagens como:
```json
{"sessionId": "...", "iteracao": 1, "duracaoMs": 1250, "finishReason": "stop", "timestamp": "..."}
```

---

## Parar os containers

```powershell
# Na pasta trabalhoESII/
# Para e remove os containers (mantém os volumes: modelos do Ollama e dados do Qdrant)
docker compose down

# Para, remove containers E os volumes (modelos baixados e documentos indexados; precisa baixar/reindexar de novo)
docker compose down -v
```

---

## Troubleshooting

### `dependency failed to start: container ...qdrant-1 is unhealthy`
Já corrigido no `compose.yaml`/`compose.retrieval-test.yaml` deste
repositório — se você ainda vir esse erro, confirme que está usando a versão
atual do compose (o healthcheck do `qdrant` mudou de `wget` para uma checagem
TCP, já que a imagem oficial não tem `wget`/`curl`). Detalhes em
[`INTEGRACAO_AGENT_RETRIEVAL.md`](INTEGRACAO_AGENT_RETRIEVAL.md).

### `ollama` nunca fica healthy / `ollama-pull` falha
O download do modelo falhou. Force o re-download com:
```powershell
docker compose up ollama -d
docker compose logs -f ollama-pull
```
Se o pull falhar por nome de modelo incorreto, verifique o nome em [https://ollama.com/library](https://ollama.com/library) e atualize `LLM_MODEL` no `compose.yaml`.

### `llm-gateway` falha no build: `No matching distribution found for py-eureka-client==0.11.14`
Versão inexistente no PyPI. Edite `llm-gateway/requirements.txt` e troque por `py-eureka-client==0.11.13`. *(Já corrigido se você atualizou o repositório.)*

### agent-service não encontra o llm-gateway ou o retrieval-service
Verifique o Eureka em [http://localhost:8761](http://localhost:8761). O `llm-gateway` e o `retrieval-service` precisam aparecer na lista de serviços registrados. Se não aparecer, veja os logs:
```powershell
docker compose logs -f llm-gateway
docker compose logs -f retrieval-service
```

### `retrieval-service` não sobe / busca sempre retorna `results: []`
- Confirme que o `qdrant` está saudável: `curl http://localhost:6333/readyz`.
- Confirme que o `ollama-pull-embed` terminou com sucesso (baixa o `nomic-embed-text`): `docker compose logs -f ollama-pull-embed`.
- Um `results: []` na busca geralmente significa que nenhum documento foi indexado ainda — rode o passo 1 da seção 4.3 antes de buscar.

### Ver logs de um serviço específico
```powershell
# Todos os logs ao vivo
docker compose logs -f

# Logs de um serviço específico
docker compose logs -f naming-server
docker compose logs -f llm-gateway
docker compose logs -f retrieval-service
docker compose logs -f qdrant
docker compose logs -f agent-service
```

### Erro de porta já em uso
Algum processo já está usando a porta. Identifique e encerre:
```powershell
netstat -ano | findstr :8761
netstat -ano | findstr :8767
netstat -ano | findstr :8766
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

Se o LLM decidir chamar a ferramenta "buscarNaBaseDeConhecimento", o agent-service
faz uma chamada extra antes de responder:

agent-service :8765
    │  Feign → descobre "retrieval-service" via Eureka (:8761)
    │  POST /api/retrieval/search  { "query": "...", "topK": 4 }
    ▼
retrieval-service :8766
    │  gera embedding da consulta via Ollama (nomic-embed-text)
    │  busca por similaridade no Qdrant
    ▼
qdrant :6333/6334  →  documentos mais relevantes
    │
    └─ resultado volta como "observação" para o ciclo ReAct do agent-service
```

> Mais detalhes sobre os ajustes feitos para conectar esses dois serviços em
> [`INTEGRACAO_AGENT_RETRIEVAL.md`](INTEGRACAO_AGENT_RETRIEVAL.md).
