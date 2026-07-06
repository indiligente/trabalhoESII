# 📊 Status Completo do Projeto — trabalhoESII

> **Data:** 06/07/2026 — 16:10  
> **Situação geral:** Núcleo agêntico funcionando. Pendências de infraestrutura para entrega final.

---

## 1. Visão Geral — O Que O Projeto Pede (PDF)

O trabalho exige uma **plataforma de microsserviços distribuídos** para agentes de IA autônomos, com:

| Requisito (PDF) | Descrição |
|:---|:---|
| **(a)** Arquitetura de microsserviços | Múltiplos serviços independentes, cada um com responsabilidade clara |
| **(b)** Comunicação Síncrona (REST/Feign) | Comunicação HTTP entre os serviços usando OpenFeign |
| **(c)** Comunicação Assíncrona (RabbitMQ) | Pelo menos um fluxo usando fila de mensagens |
| **(d)** Resiliência (Circuit Breaker) | Padrão Circuit Breaker para lidar com falhas em serviços dependentes |
| **(e)** Service Discovery (Eureka) | Naming Server para descoberta automática de serviços |
| **(f)** API Gateway | Ponto de entrada único para todas as requisições externas |
| **(g)** Observabilidade | Telemetria, logs ou métricas para monitoramento |
| **(h)** Docker Compose | Toda a plataforma orquestrada via containers |

---

## 2. Estado Atual de Cada Microsserviço

### ✅ Funcionando Corretamente

| Serviço | Porta | Status | Validação |
|:---|:---:|:---:|:---|
| **agent-service** (Spring Boot) | 8765 | ✅ Running | Ciclo ReAct completo. Chama ferramentas locais e remotas. |
| **llm-gateway** (FastAPI + Ollama) | 8767 | ✅ Running | Passa ferramentas ao LLM, retorna `tool_calls` nativos. Circuit Breaker com `pybreaker`. |
| **retrieval-service** (Spring Boot + Qdrant) | 8766 | ✅ Running | Indexação e busca vetorial funcionais. RAG validado end-to-end. |
| **tool-registry** (Spring Boot + H2) | 8400 (ext: 8082) | ✅ Running | Expõe ferramentas `calculator`, `echo`, `database-query`, `datetime`. |
| **Ollama** | 11434 | ✅ Running | Modelo `qwen2.5:3b` (chat) + `nomic-embed-text` (embeddings). |
| **Qdrant** | 6333/6334 | ✅ Running | Banco vetorial para RAG. |
| **RabbitMQ** | 5672/15672 | ✅ Running | Fila `agent.telemetry` recebe métricas do agent-service. |
| **PostgreSQL** | 5432 | ✅ Running | Banco relacional para o memory-service. |
| **Redis** | 6379 | ✅ Running | Cache para o memory-service. |

---

### ❌ Com Problemas / Não Funcionando

#### 🔴 `service-memory` (FastAPI + PostgreSQL + Redis) — Porta 8000
**Status:** `Exited (1)` — Não sobe.

**Problema 1 — Contrato de API incompatível:**
O [MemoryServiceClient.java](file:///home/indiligente/Desktop/projects/trabalhoESII/agent-service/src/main/java/TF_ESII/TF/feign/MemoryServiceClient.java) do agent-service espera:
- `GET /api/memory/{sessionId}` → retorna `List<LlmMessage>` (role + content)
- `POST /api/memory/{sessionId}` → recebe `LlmMessage`

Mas o [main.py](file:///home/indiligente/Desktop/projects/trabalhoESII/service-memory/app/main.py) do service-memory expõe:
- `POST /memories/` → recebe `{agent_id, content}` (schema `MemoryCreate`)
- `GET /memories/{agent_id}` → retorna `List<MemoryResponse>` (id, agent_id, content, created_at)

> [!CAUTION]
> **As rotas, formatos de dados e nomes de campo são completamente diferentes.** O agent-service não consegue se comunicar com o memory-service. É preciso criar rotas compatíveis no memory-service OU adaptar o Feign client.

**Problema 2 — Feign Client sem URL direta:**
O `MemoryServiceClient` usa `@FeignClient(name = "memory-service")` sem `url`, igual ao bug que corrigimos no `RetrievalServiceClient`. Sem o Eureka rodando, essa chamada falha silenciosamente (o catch no `AgentService` ignora o erro).

**Problema 3 — Driver de banco corrigido mas tabela não existe:**
Corrigimos o driver para `postgresql+asyncpg://`, mas o Alembic não é executado automaticamente ao iniciar o container. As tabelas do PostgreSQL nunca são criadas.

---

#### 🟡 `naming-server` (Spring Boot + Eureka) — Porta 8761
**Status:** Código existe na pasta `/naming-server`, mas **NÃO está no `compose.yaml`**.

**Problema adicional:** O [application.yaml](file:///home/indiligente/Desktop/projects/trabalhoESII/naming-server/src/main/resources/application.yaml) contém **conflitos de merge do Git** (linhas `<<<<<<< HEAD`, `=======`, `>>>>>>> main`), o que impede a compilação.

---

#### 🔴 `api-gateway` — **NÃO EXISTE**
Não há pasta nem código para o API Gateway no repositório. É um requisito obrigatório do PDF.

---

## 3. Requisitos do PDF — Checklist Detalhado

### (a) Arquitetura de Microsserviços ✅
Todos os 6 microsserviços de aplicação existem como projetos independentes com Dockerfiles próprios.

### (b) Comunicação Síncrona (REST/Feign) ✅
| Chamada | Via | Status |
|:---|:---|:---:|
| agent-service → llm-gateway | Feign + URL direta | ✅ |
| agent-service → tool-registry | Feign + URL direta | ✅ |
| agent-service → retrieval-service | Feign + URL direta | ✅ |
| agent-service → memory-service | Feign (sem URL!) | ❌ Contrato incompatível + sem URL |

### (c) Comunicação Assíncrona (RabbitMQ) ⚠️ Parcial
| Produtor | Fila | Consumidor | Status |
|:---|:---|:---|:---:|
| agent-service | `agent.telemetry` | **Nenhum** | ⚠️ |

> [!WARNING]
> O agent-service **publica** métricas de telemetria na fila `agent.telemetry` do RabbitMQ a cada chamada LLM (sessionId, iteração, duração, finishReason). Porém, **não existe nenhum consumidor** que leia essas mensagens. Para o requisito estar completo, é necessário pelo menos um consumer que processe/exiba os dados.

### (d) Resiliência (Circuit Breaker) ⚠️ Parcial
| Local | Implementação | Status |
|:---|:---|:---:|
| llm-gateway → Ollama | `pybreaker` (Python) — abre após 3 falhas, reseta em 30s | ✅ |
| agent-service → llm-gateway | Sem Circuit Breaker — erro 500 cru se o gateway cair | ❌ |
| api-gateway → serviços | API Gateway não existe | ❌ |

> [!IMPORTANT]
> O agent-service precisa de um **fallback** quando o llm-gateway retorna erro. Atualmente, uma falha no LLM propaga um HTTP 500 diretamente ao cliente.

### (e) Service Discovery (Eureka) ❌
O naming-server existe mas:
1. Não está no `compose.yaml`
2. O `application.yaml` tem conflitos de merge do Git
3. Todos os microsserviços estão com `EUREKA_CLIENT_REGISTER_WITH_EUREKA: false`

### (f) API Gateway ❌
Não existe no repositório. Precisa ser criado do zero.

### (g) Observabilidade ⚠️ Parcial
- ✅ Métricas publicadas no RabbitMQ (telemetria de cada chamada LLM)
- ✅ Endpoints `/health` nos serviços Java
- ✅ Spring Boot Actuator habilitado (`management.endpoints.web.exposure.include: "*"`)
- ❌ Não há consumidor das métricas nem dashboard

### (h) Docker Compose ✅
Toda a plataforma está orquestrada via `compose.yaml` com rede bridge compartilhada.

---

## 4. Fluxos Validados com Sucesso

### Fluxo RAG Completo ✅
```
Usuário → agent-service → llm-gateway (Ollama qwen2.5:3b)
                               ↓ tool_calls: buscarNaBaseDeConhecimento
                         agent-service → retrieval-service → Qdrant
                               ↓ resultado da busca vetorial
                         agent-service → llm-gateway (resposta final)
                               ↓
                         Resposta ao Usuário (com dados do RAG)
```
**Teste validado:** *"onde fica a sede da empresa Nubo?"* → respondeu corretamente *"Porto Alegre, fundada em 2015"*.

### Fluxo de Ferramentas Remotas (Tool Registry) ⏳ Pendente validação
```
Usuário → agent-service → llm-gateway (Ollama)
                               ↓ tool_calls: calculator
                         agent-service → tool-registry → executa cálculo
                               ↓ resultado
                         Resposta ao Usuário
```
**Teste:** Aguardando validação com `98234 * 87234`.

### Telemetria Assíncrona (RabbitMQ) ✅ Publicação
A cada chamada LLM, o agent-service publica na fila `agent.telemetry`:
```json
{
  "sessionId": "session-rag4",
  "iteracao": 1,
  "duracaoMs": 12345,
  "finishReason": "tool_calls",
  "timestamp": "2026-07-06T19:05:00Z"
}
```
Pode ser verificado no painel do RabbitMQ: http://localhost:15672 (user: `guest`, pass: `guest`).

---

## 5. Roteiro de Finalização (Priorizado)

Dado o tempo limitado, aqui está a ordem recomendada:

### 🔴 Prioridade ALTA (Obrigatório para entrega)

#### Tarefa 1: Corrigir o `naming-server` e adicionar ao compose (~15min)
1. Limpar os conflitos de merge do Git no `application.yaml`
2. Adicionar o serviço `naming-server` ao `compose.yaml`
3. Alterar os microsserviços Java para `EUREKA_CLIENT_REGISTER_WITH_EUREKA: true`

#### Tarefa 2: Criar o `api-gateway` (~30min)
1. Criar projeto Spring Boot com `Spring Cloud Gateway` + `Eureka Client`
2. Configurar rotas para os serviços internos
3. Adicionar ao `compose.yaml`

#### Tarefa 3: Corrigir o `service-memory` (~20min)
1. Adicionar rotas `/api/memory/{sessionId}` compatíveis com o Feign client do agent-service
2. Adicionar URL direta ao `MemoryServiceClient` do Feign
3. Executar Alembic para criar as tabelas
4. Adicionar variável `MEMORY_SERVICE_URL` no compose

#### Tarefa 4: Adicionar fallback/Circuit Breaker no agent-service (~10min)
1. Envolver a chamada `llmGatewayClient.chat()` em try-catch
2. Retornar mensagem amigável de fallback ao usuário

### 🟡 Prioridade MÉDIA (Desejável)

#### Tarefa 5: Criar consumidor de telemetria RabbitMQ (~15min)
Criar um `@RabbitListener` no agent-service (ou novo serviço) que consuma da fila `agent.telemetry` e logue as métricas, completando o fluxo assíncrono.

#### Tarefa 6: Testes da calculadora e data/hora (~5min)
Validar as ferramentas remotas do tool-registry via agente.

### 🟢 Prioridade BAIXA (Bônus)

#### Tarefa 7: Dashboard de observabilidade
Configurar Prometheus/Grafana ou simplesmente um endpoint que exponha as métricas consumidas do RabbitMQ.

---

## 6. Comandos de Teste Rápido

```bash
# Verificar quais containers estão rodando
sudo docker compose ps -a

# Testar health de cada serviço
curl http://localhost:8765/api/agent/health     # agent-service
curl http://localhost:8766/api/retrieval/health  # retrieval-service
curl http://localhost:8767/health                # llm-gateway

# Indexar documento no RAG
curl -X POST http://localhost:8766/api/retrieval/documents \
     -H "Content-Type: application/json" \
     -d '{"content": "A sede oficial da empresa Nubo é em Porto Alegre, fundada em 2015."}'

# Testar RAG via Agente
curl -X POST http://localhost:8765/api/agent/chat \
     -H "Content-Type: application/json" \
     -d '{"sessionId": "session-rag", "message": "onde fica a sede da empresa Nubo?"}'

# Testar calculadora via Agente
curl -X POST http://localhost:8765/api/agent/chat \
     -H "Content-Type: application/json" \
     -d '{"sessionId": "session-calc", "message": "quanto é 98234 * 87234?"}'

# Verificar mensagens no RabbitMQ (painel web)
# http://localhost:15672 → Queues → agent.telemetry → Get Messages
```
