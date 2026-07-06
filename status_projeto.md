# Status Completo do Projeto - trabalhoESII

> **Data:** 06/07/2026 - 19:55  
> **Situacao geral:** infraestrutura principal integrada. Eureka, API Gateway e fluxo inicial do agente via gateway ja foram validados.

---

## 1. Objetivo do Trabalho

O projeto deve entregar uma plataforma de microsservicos distribuida para agentes de IA autonomos, cobrindo:

| Requisito | Situacao atual |
|:---|:---:|
| Arquitetura de microsservicos | ✅ |
| Comunicacao sincrona REST/Feign | ✅ |
| Comunicacao assincrona RabbitMQ | ✅ Implementada |
| Resiliencia / Circuit Breaker | ⚠️ Parcial |
| Service Discovery com Eureka | ✅ |
| API Gateway | ✅ |
| Observabilidade | ⚠️ Parcial |
| Docker Compose | ✅ |

---

## 2. Estado Atual dos Servicos

| Servico | Porta externa | Status | Observacao |
|:---|:---:|:---:|:---|
| `api-gateway` | 8080 | ✅ Validado | Roteia `/agent-service/**`, `/retrieval-service/**`, `/tool-registry/**`, `/llm-gateway/**` e `/service-memory/**`. |
| `naming-server` / Eureka | 8761 | ✅ Validado | `API-GATEWAY`, `AGENT-SERVICE`, `RETRIEVAL-SERVICE` e `TOOL-REGISTRY` aparecem como `UP`. |
| `agent-service` | 8765 | ✅ Validado | Responde via gateway e executa chamada real ao LLM. |
| `llm-gateway` | 8767 | ✅ Validado | Integra com Ollama e retorna resposta para o agente. |
| `retrieval-service` | 8766 | ⏳ A validar via gateway | Health, indexacao e busca precisam ser retestados passando pelo API Gateway. |
| `tool-registry` | 8082 -> 8400 | ⏳ A validar via gateway | Listagem e execucao de ferramenta precisam ser retestadas passando pelo API Gateway. |
| `service-memory` | 8000 | ⏳ A validar | Ja possui rotas `/api/memory/{sessionId}`, mas falta validar persistencia real com o agente. |
| RabbitMQ | 5672 / 15672 | ✅ Implementado | O agente publica telemetria em `agent.telemetry` e possui consumidor com `@RabbitListener`. |
| Qdrant | 6333 / 6334 | ✅ Usado pelo RAG | Banco vetorial do `retrieval-service`. |
| Ollama | 11434 | ✅ Usado pelo LLM/RAG | Modelos `qwen2.5:3b` e `nomic-embed-text`. |
| PostgreSQL | 5432 | ⏳ A validar com memoria | Banco do `service-memory`. |
| Redis | 6379 | ⏳ A validar com memoria | Cache do `service-memory`. |

---

## 3. Validacoes Ja Feitas

### Eureka

Comando:

```bash
curl http://localhost:8761/eureka/apps
```

Resultado observado:

- `API-GATEWAY` registrado e `UP`
- `AGENT-SERVICE` registrado e `UP`
- `RETRIEVAL-SERVICE` registrado e `UP`
- `TOOL-REGISTRY` registrado e `UP`

### API Gateway -> Agent Service -> LLM

Comando validado:

```bash
curl -i -X POST http://localhost:8080/agent-service/api/agent/nova-sessao \
  -H "Content-Type: application/json" \
  -d '{"message":"Ola, crie uma nova sessao e me responda oi"}'
```

Resultado observado:

- HTTP `200`
- retorno com `sessionId`
- resposta gerada pelo LLM
- `toolsUsed: []`
- `iterations: 1`

Conclusao: o gateway removeu corretamente o prefixo `/agent-service`, encaminhou para `/api/agent/nova-sessao`, o agente chamou o LLM e retornou resposta ao cliente.

---

## 4. Proximas Integracoes a Validar

### 4.1 API Gateway -> Retrieval Service

Por que e necessario:

- comprova que o gateway nao funciona apenas para o `agent-service`;
- valida o requisito de ponto unico de entrada para o RAG;
- confirma que endpoints de indexacao e busca vetorial continuam acessiveis depois da introducao do gateway;
- prepara o teste do fluxo completo `usuario -> gateway -> agent-service -> retrieval-service -> Qdrant`.

Comandos:

```bash
curl http://localhost:8080/retrieval-service/api/retrieval/health
```

```bash
curl -X POST http://localhost:8080/retrieval-service/api/retrieval/documents \
  -H "Content-Type: application/json" \
  -d '{"content":"A sede oficial da empresa Nubo e em Porto Alegre, fundada em 2015."}'
```

```bash
curl -X POST http://localhost:8080/retrieval-service/api/retrieval/search \
  -H "Content-Type: application/json" \
  -d '{"query":"onde fica a sede da empresa Nubo?","topK":3}'
```

### 4.2 API Gateway -> Tool Registry

Por que e necessario:

- comprova que o catalogo de ferramentas esta exposto pelo ponto unico de entrada;
- valida que ferramentas remotas podem ser listadas e executadas sem acessar a porta interna do servico diretamente;
- prepara o fluxo agêntico em que o LLM escolhe uma ferramenta e o `agent-service` executa no `tool-registry`;
- ajuda a demonstrar comunicacao sincrona entre microsservicos.

Comandos:

```bash
curl http://localhost:8080/tool-registry/tools
```

```bash
curl -X POST http://localhost:8080/tool-registry/tools/calculator/execute \
  -H "Content-Type: application/json" \
  -d '{"params":{"expression":"98234 * 87234"}}'
```

---

## 5. Fluxos de Negocio a Validar

### Fluxo RAG completo via Gateway

Objetivo: provar o caminho completo:

```text
Cliente -> API Gateway -> Agent Service -> LLM Gateway -> Tool call local
        -> Retrieval Service -> Qdrant -> Agent Service -> LLM Gateway -> resposta
```

Comando:

```bash
curl -X POST http://localhost:8080/agent-service/api/agent/chat \
  -H "Content-Type: application/json" \
  -d '{"sessionId":"session-rag-gateway","message":"onde fica a sede da empresa Nubo?"}'
```

Resultado esperado:

- resposta mencionando Porto Alegre;
- `toolsUsed` contendo `buscarNaBaseDeConhecimento`, se o LLM decidir usar RAG;
- mais de uma iteracao quando houver chamada de ferramenta.

### Fluxo de ferramenta remota via Tool Registry

Objetivo: provar o caminho:

```text
Cliente -> API Gateway -> Agent Service -> LLM Gateway
        -> Tool Registry -> ferramenta calculator -> Agent Service -> resposta
```

Comando:

```bash
curl -X POST http://localhost:8080/agent-service/api/agent/chat \
  -H "Content-Type: application/json" \
  -d '{"sessionId":"session-calc-gateway","message":"quanto e 98234 * 87234? Use uma ferramenta se precisar."}'
```

Resultado esperado:

- resposta com o valor correto da multiplicacao;
- `toolsUsed` contendo `calculator`, se o LLM escolher a ferramenta remota.

---

## 6. Pendencias Funcionais

### 6.1 `service-memory`

Estado atual:

- o `agent-service` chama `GET /api/memory/{sessionId}` e `POST /api/memory/{sessionId}`;
- o `service-memory` ja expoe essas rotas;
- o compose executa `alembic upgrade head` antes de subir o FastAPI;
- o `MemoryServiceClient` ja usa `MEMORY_SERVICE_URL`.

O que falta validar:

- se o container sobe estavel;
- se o agente consegue salvar mensagens sem erro;
- se uma segunda chamada com a mesma `sessionId` recupera o historico;
- se o JSON retornado pelo FastAPI e aceito pelo `LlmMessage` do Java.

Comandos sugeridos:

```bash
curl http://localhost:8080/service-memory/api/memory/session-teste
```

```bash
curl -X POST http://localhost:8080/service-memory/api/memory/session-teste \
  -H "Content-Type: application/json" \
  -d '{"role":"user","content":"Meu nome e Ana."}'
```

```bash
curl http://localhost:8080/service-memory/api/memory/session-teste
```

### 6.2 Consumidor RabbitMQ

Estado atual:

- o `agent-service` publica metricas na fila `agent.telemetry`;
- existe um consumidor `@RabbitListener` para `agent.telemetry`;
- a fila e declarada como duravel pelo `agent-service`;
- as mensagens sao serializadas como JSON e logadas no console do servico.

O que falta validar:

- rebuildar o `agent-service`;
- fazer uma chamada ao agente;
- verificar nos logs a linha `Telemetry event received`.

---

## 7. Roteiro Priorizado

1. Validar `retrieval-service` via gateway.
2. Validar `tool-registry` via gateway.
3. Validar RAG completo via gateway.
4. Validar calculadora remota via agente.
5. Validar ou ajustar `service-memory`.
6. Adicionar Prometheus/Grafana para observabilidade.
7. Atualizar `COMO_EXECUTAR.md` com os comandos novos via gateway.

---

## 8. Comandos de Apoio

Ver containers:

```bash
docker compose ps -a
```

Logs principais:

```bash
docker compose logs -f --tail=200 api-gateway naming-server agent-service retrieval-service tool-registry
```

Logs da memoria:

```bash
docker compose logs -f --tail=200 service-memory postgres redis
```

Logs de telemetria:

```bash
docker compose logs -f --tail=200 rabbitmq agent-service
```

Painel RabbitMQ:

```text
http://localhost:15672
usuario: guest
senha: guest
```
