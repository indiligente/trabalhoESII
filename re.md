# Relatório Técnico: Plataforma de Agentes Conversacionais com Infraestrutura de Microsserviços

**Autores:** Bryan Coelho, Diego Mello, Gabriel Gautério, Leonardo Pasa e Manuel Soares Da Luz  
**Disciplina:** Engenharia de Software II  
**Instituição:** Pontifícia Universidade Católica do Rio Grande do Sul  

---

## 1. Introdução

Este relatório documenta as decisões de projeto, os experimentos realizados e a implementação do Produto Mínimo Viável (MVP) de uma plataforma para execução de agentes de Inteligência Artificial (IA) conversacionais. O sistema foi concebido para operar de forma local — sem depender de recursos de nuvem — utilizando Docker Compose para orquestrar todos os serviços necessários.

A plataforma permite que um agente de IA receba requisições de um usuário, raciocine sobre como resolvê-las por meio de um modelo de linguagem (LLM) executado localmente via Ollama, invoque ferramentas externas quando necessário, observe os resultados obtidos e repita o ciclo até produzir uma resposta final. Esse comportamento segue o padrão ReAct (Raciocínio → Ação → Observação), que é o estado da arte em agentes autônomos.

A escolha de uma arquitetura de microsserviços foi motivada tanto pelo requisito do trabalho quanto pela necessidade prática de permitir que cada membro do grupo desenvolvesse seu serviço de forma independente. Essa separação nos permitiu trabalhar em paralelo sem conflitos constantes de merge e, ao mesmo tempo, aprender na prática os desafios de integração que surgem quando sistemas distribuídos precisam funcionar em conjunto.

---

## 2. Arquitetura da Plataforma

A arquitetura foi estruturada em sete microsserviços independentes, cada um com sua própria responsabilidade bem definida. A comunicação síncrona entre os serviços ocorre via protocolo REST (usando OpenFeign nos serviços Java e requisições HTTP nos serviços Python), enquanto eventos assíncronos — como telemetria de chamadas ao LLM — trafegam por meio do RabbitMQ como message broker.

O diagrama a seguir ilustra a visão geral da arquitetura e os fluxos de comunicação entre os componentes:

```
┌──────────────────────────────────────────────────────────────────────────┐
│                        PONTO DE ENTRADA                                 │
│                                                                          │
│                   api-gateway (:8080)                                    │
│                   Spring Cloud Gateway                                   │
│                   5 rotas configuradas                                   │
└────────────┬──────────┬──────────┬──────────┬──────────┬────────────────┘
             │lb://      │lb://      │lb://      │URL        │URL
             ▼           ▼           ▼           ▼           ▼
    ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────────┐
    │  agent   │ │retrieval │ │  tool    │ │   llm    │ │   service    │
    │ service  │ │ service  │ │ registry │ │ gateway  │ │   memory     │
    │  :8765   │ │  :8766   │ │  :8400   │ │  :8767   │ │    :8000     │
    │ Spring   │ │ Spring   │ │ Spring   │ │ FastAPI  │ │   FastAPI    │
    │  Boot    │ │Boot + AI │ │Boot + H2 │ │+pybreaker│ │+PG + Redis   │
    └──┬───────┘ └──────────┘ └──────────┘ └────┬─────┘ └──────────────┘
       │                                        │
       │   Feign + Eureka / URL direta          │
       ├───────────────────────────────────────►│  Ollama (:11434)
       │                                        │  qwen2.5:0.5b
       │   RabbitMQ (assíncrono)                │
       ├──────────► RabbitMQ (:5672)            │
       │            fila: agent.telemetry       │
       │                                        │
  ┌────┴─────────────────────────────────────────┴──────────────┐
  │                    INFRAESTRUTURA                            │
  │  Eureka (:8761)  │  PostgreSQL (:5432)  │  Redis (:6379)   │
  │  Qdrant (:6333)  │  Prometheus (:9090)  │  Grafana (:3000) │
  └─────────────────────────────────────────────────────────────┘
```

### 2.1. Componentes e Responsabilidades

| Microsserviço | Responsabilidade | Tecnologia Adotada |
|:---|:---|:---|
| **Agent Service** | Recebe requisições do usuário, orquestra chamadas ao LLM e ferramentas, mantendo o ciclo agêntico (ReAct). É o "cérebro" da plataforma. | Spring Boot 4.0.6 (Java 21) com OpenFeign, Resilience4J e RabbitMQ |
| **LLM Gateway** | Proxy unificado para o modelo de linguagem local. Abstrai o provedor de IA e gerencia resiliência com circuit breaker próprio. | Python (FastAPI) + Ollama SDK + pybreaker (modelo Qwen 2.5 0.5b) |
| **API Gateway** | Roteamento e ponto de entrada único do sistema. Todas as requisições externas passam por aqui antes de serem direcionadas ao serviço correto. | Spring Cloud Gateway (Spring Boot 4.1.0) |
| **Name Server** | Descoberta de serviços (Service Registry). Os serviços Spring se registram automaticamente e são localizados pelo nome lógico. | Eureka Server (Spring Cloud Netflix) |
| **Memory Service** | Armazena e recupera o histórico de conversação. Usa PostgreSQL como persistência durável e Redis como cache de leitura. | Python (FastAPI) + PostgreSQL (asyncpg) + Redis + Alembic para migrations |
| **Retrieval Service** | Busca semântica em documentos para o pipeline RAG (Retrieval-Augmented Generation). Gera embeddings e faz similarity search. | Spring Boot 4.0.6 + Spring AI 2.0.0-M8 + Qdrant (vector store) + Ollama (nomic-embed-text) |
| **Tool Registry** | Registra, expõe e executa ferramentas que os agentes podem invocar (calculadora, consulta a banco, echo, datetime). | Spring Boot 3.4.1 + H2 (in-memory) + JPA |

---

## 3. Decisões de Arquitetura e Justificativas

Nesta seção, detalhamos as escolhas técnicas feitas ao longo do desenvolvimento, os trade-offs que identificamos e as dificuldades que enfrentamos.

### LLM Gateway

Optamos por implementar o LLM Gateway em Python com FastAPI ao invés de Java por uma questão prática: o ecossistema Python oferece uma SDK oficial do Ollama (`ollama>=0.3.0`) com suporte nativo a chamadas assíncronas (`AsyncClient`), o que simplificou bastante a integração. A escolha do modelo **Qwen 2.5 (0.5b)** foi baseada em testes práticos — precisávamos de um modelo leve o suficiente para rodar na máquina local sem exigir GPU dedicada, e o Qwen 2.5 ofereceu um bom equilíbrio entre capacidade de raciocínio e consumo de recursos.

Para garantir resiliência, adicionamos um circuit breaker com `pybreaker` diretamente no serviço (`fail_max=3`, `reset_timeout=30s`). Se o Ollama falhar três vezes seguidas, o circuit breaker abre e retorna erro 503 imediatamente, evitando que requisições fiquem travadas esperando timeout. As ferramentas (tools) são recebidas via JSON Schema no corpo da requisição e repassadas diretamente ao Ollama para habilitar o Function Calling nativo do modelo.

**Trade-off:** Usar Python para o gateway introduz uma heterogeneidade na stack (a maioria dos serviços é Java), mas o ganho em produtividade e qualidade da integração com o Ollama compensou essa escolha.

### Agent Service

O Agent Service foi projetado para ser exclusivamente um orquestrador — ele não acessa o LLM nem bancos de dados diretamente. Em vez disso, delega tudo via OpenFeign: chamadas ao LLM passam pelo `LlmGatewayClient`, memória pelo `MemoryServiceClient`, ferramentas remotas pelo `ToolRegistryClient` e busca semântica pelo `RetrievalServiceClient`.

O ciclo agêntico é implementado como um loop de até 10 iterações. A cada iteração, o agente envia o histórico completo ao LLM e analisa a resposta: se o LLM solicitar tool calls, o agente executa as ferramentas e adiciona os resultados como mensagens de observação no histórico; se o LLM retornar texto livre (sem tool calls), a resposta final é enviada ao usuário.

Os Feign Clients dos serviços Java (`tool-registry`, `retrieval-service`) usam Eureka para resolver o endereço, ou seja, o Agent Service não precisa saber o IP ou porta dos serviços — ele encontra pelo nome lógico registrado no Eureka. Já para os serviços Python (`llm-gateway`, `service-memory`), usamos URL direta configurável via variável de ambiente, pois esses serviços não se registram no Eureka.

Cada chamada ao LLM gera automaticamente um evento de telemetria publicado na fila `agent.telemetry` do RabbitMQ, contendo: `sessionId`, número da iteração, duração em milissegundos e `finishReason`. Essa publicação é fire-and-forget — se o RabbitMQ estiver indisponível, a exceção é silenciada e o fluxo do agente não é interrompido.

### Memory Service (Banco de Dados / Memória)

Utilizamos **PostgreSQL** como banco de dados persistente para o histórico de conversação, implementado em Python com FastAPI. A escolha de Python aqui foi prática: como dois dos cinco membros do grupo tinham mais familiaridade com Python, fazia sentido dividir a responsabilidade. Além disso, a combinação FastAPI + SQLAlchemy (async) + Alembic nos deu um ciclo de desenvolvimento rápido — as migrations são gerenciadas via Alembic, e o servidor roda com `uvicorn` assincrono.

Para otimizar leituras repetidas do mesmo histórico de sessão, adicionamos uma camada de cache com **Redis** (TTL de 1 hora). Quando uma nova mensagem é salva via POST, o cache daquela sessão é invalidado automaticamente. Se o Redis estiver indisponível, o sistema simplesmente ignora o cache e consulta diretamente o PostgreSQL — nunca retornamos erro ao usuário por falha de cache.

**Trade-off:** Não implementamos memória de curto prazo separada (como sugerido no enunciado com Redis para curto prazo e PostgreSQL para longo prazo). Na nossa implementação, o Redis atua exclusivamente como cache de leitura e o PostgreSQL guarda todo o histórico. Essa simplificação nos permitiu entregar o serviço funcional sem complicar desnecessariamente a lógica de negócio.

### Retrieval Service (RAG)

O Retrieval Service implementa o pipeline de Retrieval-Augmented Generation (RAG) usando **Spring AI** como framework de integração. Escolhemos Spring AI porque ele já oferece abstrações prontas para vector stores e embeddings, o que nos evitou de escrever código boilerplate para conexão com o Qdrant e com o Ollama.

O banco vetorial utilizado é o **Qdrant**, que armazena os embeddings dos documentos indexados. Para gerar os embeddings, utilizamos o modelo **nomic-embed-text** rodando localmente no Ollama — assim como o LLM principal, o modelo de embeddings também é 100% local.

O serviço expõe dois endpoints principais:
- `POST /api/retrieval/documents` — recebe um documento (texto + metadados) e o indexa no Qdrant
- `POST /api/retrieval/search` — recebe uma consulta e retorna os documentos mais relevantes por similaridade semântica (com threshold de 0.3)

A busca semântica é invocada pelo Agent Service através da ferramenta local `buscarNaBaseDeConhecimento`, que por sua vez chama o `RetrievalServiceClient` via Feign + Eureka.

**Trade-off:** O Spring AI 2.0.0-M8 ainda é uma milestone (pré-release), o que nos trouxe alguns problemas de compatibilidade inicial com o Spring Boot 4.0.6. Porém, a alternativa seria implementar toda a lógica de embeddings e busca vetorial manualmente, o que seria consideravelmente mais trabalhoso.

---

## 4. Dependências e Bibliotecas

Para viabilizar a arquitetura e a integração com os modelos de IA, o projeto utiliza um conjunto específico de bibliotecas e starters. A tabela a seguir lista as principais dependências dos serviços Java:

| Dependência | Descrição e Propósito |
|:---|:---|
| `spring-boot-starter-web` | Fornece o framework web (Spring MVC) para criar endpoints REST nos serviços Java. |
| `spring-boot-starter-actuator` + `micrometer-registry-prometheus` | Expõe métricas do serviço (JVM, HTTP, circuit breakers) no formato Prometheus via `/actuator/prometheus`. |
| `spring-cloud-starter-netflix-eureka-client` | Registra o serviço no Eureka Server e permite descoberta de outros serviços pelo nome lógico. |
| `spring-cloud-starter-netflix-eureka-server` | Starter usado exclusivamente no naming-server para funcionar como o servidor Eureka. |
| `spring-cloud-starter-gateway-mvc` | Habilita o roteamento de requisições no api-gateway com Spring Cloud Gateway (modelo MVC). |
| `spring-cloud-starter-openfeign` | Permite criar clientes REST declarativos (interfaces Java anotadas) para comunicação entre serviços. |
| `resilience4j-spring-boot3` | Implementação de circuit breaker via anotação `@CircuitBreaker` no agent-service. Cada serviço externo tem sua instância configurada independentemente. |
| `spring-amqp` + `spring-rabbit` | Integração com RabbitMQ para publicação e consumo assíncronos de mensagens (telemetria). |
| `spring-ai-starter-model-ollama` | Integração do Spring AI com o Ollama para execução de modelos de linguagem locais. Usado no retrieval-service para gerar embeddings. |
| `spring-ai-starter-vector-store-qdrant` | Conector para o banco vetorial Qdrant, responsável por armazenar e buscar embeddings para o pipeline RAG. |
| `spring-ai-advisors-vector-store` | Módulo do Spring AI para gerenciar o contexto de busca vetorial e automatizar a injeção de resultados no pipeline. |

Para os serviços Python, as dependências são:

| Serviço | Dependência | Propósito |
|:---|:---|:---|
| LLM Gateway | `fastapi`, `uvicorn`, `pydantic` | Framework web assíncrono e validação de schemas |
| LLM Gateway | `ollama>=0.3.0` | SDK oficial do Ollama com `AsyncClient` |
| LLM Gateway | `pybreaker` | Circuit breaker em Python (fail_max=3, reset_timeout=30s) |
| Memory Service | `fastapi`, `uvicorn`, `pydantic-settings` | Framework web e configuração |
| Memory Service | `sqlalchemy`, `asyncpg` | ORM assíncrono + driver PostgreSQL |
| Memory Service | `redis` | Cliente Redis assíncrono para cache |
| Memory Service | `alembic` | Gerenciamento de migrations do banco |

### 4.1. Gerenciamento de Versões (BOMs)

As dependências `spring-cloud-dependencies` e `spring-ai-bom` centralizam as definições de versão por meio do mecanismo de BOM (Bill of Materials) do Maven. Isso garante que todas as dependências de um mesmo ecossistema usem versões compatíveis entre si, evitando conflitos de dependência transitiva. Na prática, isso significou que não precisamos declarar versões individuais para cada módulo do Spring Cloud ou do Spring AI — o BOM cuida disso automaticamente.

---

## 5. Resiliência e Comunicação Assíncrona

### 5.1. Circuit Breaker

Para evitar falhas em cascata que pudessem comprometer a plataforma como um todo, implementamos o padrão Circuit Breaker em dois níveis:

**No Agent Service (Java — Resilience4J):** Configuramos cinco instâncias independentes de circuit breaker, uma para cada serviço externo: `llmGateway`, `memoryService` (load + save), `toolRegistry` (list + execute) e `retrievalService`. A configuração é baseada em contagem (sliding window de 5 chamadas), com threshold de falha de 50%, e o circuito permanece aberto por 20 segundos antes de tentar a recuperação (half-open com 2 chamadas de teste).

Cada instância possui um método de fallback dedicado. Por exemplo, quando o LLM Gateway está indisponível, o fallback retorna a mensagem "O LLM Gateway está temporariamente indisponível. Tente novamente em alguns instantes." com `finishReason: circuit_breaker_fallback`. Quando o serviço de memória cai, o fallback retorna uma lista vazia e o agente continua funcionando sem histórico. Quando o tool-registry cai, o agente opera sem ferramentas remotas.

**No LLM Gateway (Python — pybreaker):** O circuit breaker do `pybreaker` protege a chamada ao Ollama com `fail_max=3` e `reset_timeout=30s`. Se o Ollama falhar três vezes consecutivas, o circuit breaker abre e o gateway retorna HTTP 503 imediatamente, sem sequer tentar conectar.

### 5.2. Comunicação Assíncrona (RabbitMQ)

A mensageria assíncrona foi implementada utilizando **RabbitMQ** como broker central, com foco no fluxo de telemetria:

- **Produtor:** O Agent Service publica eventos a cada iteração do ciclo agêntico na fila durável `agent.telemetry`. Cada evento contém: `sessionId`, número da iteração, duração da chamada ao LLM em milissegundos, `finishReason` e timestamp. A publicação é não bloqueante — se o RabbitMQ estiver indisponível, o catch silencia a exceção e o agente continua operando normalmente.

- **Consumidor:** O `TelemetryConsumer` (anotado com `@RabbitListener`) consome os eventos da fila e os registra em log estruturado. Isso possibilita que, em uma evolução futura, esses dados sejam persistidos em um banco de séries temporais para análise posterior.

- **Configuração:** A fila é declarada como durável via `QueueBuilder.durable()` e as mensagens são serializadas em JSON pelo `Jackson2JsonMessageConverter`, garantindo compatibilidade de tipos entre o produtor (que envia `Map<String, Object>`) e o consumidor.

**Importância do padrão assíncrono:** Sem o RabbitMQ, a publicação de telemetria seria síncrona — cada chamada ao LLM travaria até que a telemetria fosse registrada. Com a fila assíncrona, o agente publica o evento e segue imediatamente para a próxima iteração, reduzindo a latência percebida pelo usuário.

---

## 6. Observabilidade e Conteinerização

### 6.1. Observabilidade (Prometheus + Grafana)

Para a camada de observabilidade, optamos por **Prometheus + Grafana** ao invés de OpenTelemetry + Jaeger. A razão principal foi pragmática: todos os nossos serviços Java já exportam métricas automaticamente via `micrometer-registry-prometheus` e Spring Boot Actuator, sem necessidade de instrumentação manual. O Prometheus coleta essas métricas via scraping a cada 15 segundos.

O arquivo `prometheus.yml` configura cinco jobs de scraping:
- `api-gateway` (porta 8080)
- `agent-service` (porta 8765)
- `retrieval-service` (porta 8766)
- `tool-registry` (porta 8400)
- `naming-server` (porta 8761)

Cada job acessa o endpoint `/actuator/prometheus` do respectivo serviço, que expõe métricas como:
- Latência de requisições HTTP (p50, p95, p99)
- Contagem de requisições por status code
- Estado dos circuit breakers (open, closed, half-open)
- Métricas de JVM (heap, GC, threads)
- Uptime do serviço

O **Grafana** é provisionado automaticamente via volume bind-mount (`observability/grafana/provisioning/`) com o Prometheus já configurado como datasource. Isso significa que ao subir o compose, o Grafana já está pronto para consultar dados — basta acessar `http://localhost:3000` (admin/admin) e criar dashboards.

**Limitação reconhecida:** Não implementamos rastreamento distribuído (distributed tracing) com OpenTelemetry + Jaeger. Isso significa que não conseguimos visualizar o caminho completo de uma requisição entre todos os microsserviços em uma timeline unificada. Em uma evolução futura, seria valioso adicionar esse nível de rastreamento.

### 6.2. Conteinerização (Docker Compose)

Todos os sete microsserviços foram empacotados individualmente com Dockerfiles (multi-stage builds para os serviços Java, imagens slim para os serviços Python). O `compose.yaml` orquestra **16 containers** no total:

| Categoria | Containers |
|:---|:---|
| Microsserviços de aplicação | agent-service, llm-gateway, retrieval-service, tool-registry, service-memory, naming-server, api-gateway |
| Infraestrutura | ollama, qdrant, rabbitmq, postgres, redis |
| Setup | ollama-pull-embed, ollama-pull-llm (baixam modelos automaticamente) |
| Observabilidade | prometheus, grafana |

O compose define healthchecks para os serviços críticos (postgres, redis, naming-server, service-memory) e usa `depends_on` com `condition: service_healthy` para garantir a ordem correta de inicialização. Por exemplo, o `agent-service` só sobe depois que o `naming-server` estiver saudável, o `rabbitmq` estiver iniciado e o `service-memory` estiver respondendo no `/health`.

Todos os containers compartilham uma rede bridge (`platform_network`), e os dados persistentes (PostgreSQL, Qdrant, Ollama models, Grafana) são armazenados em volumes nomeados Docker.

---

## 7. Migração para Produção em Nuvem (Kubernetes)

Para transpor a plataforma do Docker Compose local para um cluster Kubernetes em produção, identificamos as seguintes alterações arquiteturais necessárias:

### Descoberta de Serviços

O servidor Eureka seria **desativado**, já que o Kubernetes oferece descoberta de serviços nativa via DNS interno (CoreDNS). Cada Service do Kubernetes recebe automaticamente um endereço DNS no formato `<nome-do-service>.<namespace>.svc.cluster.local`. Os Feign Clients seriam reconfigurados para usar esses endereços ao invés de `lb://NOME-DO-SERVICO` do Eureka. Na prática, bastaria trocar `@FeignClient(name = "tool-registry")` por `@FeignClient(name = "tool-registry", url = "${TOOL_REGISTRY_URL}")` e definir a URL via ConfigMap.

### Configuração e Segurança

As variáveis de ambiente que hoje estão expostas diretamente no `compose.yaml` (como `POSTGRES_USER: root`, `POSTGRES_PASSWORD: root`, `GF_SECURITY_ADMIN_PASSWORD: admin`) seriam migradas para **ConfigMaps** (configurações não-sensíveis) e **Secrets** (credenciais) do Kubernetes. Secrets podem ser encriptados em repouso e acessados somente pelos Pods autorizados via RBAC, o que é uma melhoria significativa de segurança comparado ao compose atual.

### Armazenamento Persistente

Os volumes locais do Docker (`db_data`, `ollama_data`, `qdrant_data`, `grafana_data`) seriam substituídos por **PersistentVolumeClaims (PVCs)** provisionados dinamicamente no provedor de nuvem. No caso de GCP, usaríamos `storageClassName: standard` (discos persistentes padrão) para PostgreSQL e Qdrant, garantindo que os dados sobrevivam a reinícios e realocações de Pods. Para o Ollama, os modelos poderiam ser pré-carregados na imagem Docker (baked-in), eliminando a necessidade do volume e dos containers `ollama-pull-*`.

Em produção, o PostgreSQL e o Redis idealmente seriam substituídos por serviços gerenciados do provedor de nuvem (ex.: Cloud SQL e Memorystore no GCP, ou RDS e ElastiCache na AWS), eliminando a responsabilidade operacional de backup, patching e alta disponibilidade.

### Escalonamento Automático (HPA)

O **Horizontal Pod Autoscaler (HPA)** seria configurado para os serviços mais exigidos:

- **Agent Service:** Escala com base em utilização de CPU (target 70%), pois cada requisição de chat gera múltiplas chamadas síncronas e o consumo de CPU é proporcional à carga.
- **LLM Gateway + Ollama:** Este é o gargalo principal. O modelo de linguagem consome GPU (ou muita CPU), então o escalonamento dependeria de suporte a GPU no cluster (ex.: node pools com GPU no GKE). Sem GPU, o escalonamento horizontal ajuda pouco, pois cada instância do Ollama precisa carregar o modelo inteiro na memória.
- **Retrieval Service:** Escala horizontalmente de forma natural, pois cada instância é stateless — o estado fica no Qdrant.

### Ingress e TLS

O API Gateway seria substituído (ou complementado) por um **Ingress Controller** do Kubernetes (como NGINX Ingress), que gerenciaria TLS/SSL com certificados provisionados automaticamente via cert-manager + Let's Encrypt. O roteamento por path que hoje é feito no Spring Cloud Gateway poderia ser replicado com Ingress rules.

---

## 8. Oportunidades de Melhoria e Riscos Identificados

Avaliando o MVP de forma crítica, destacamos os seguintes riscos e melhorias estruturais:

### Performance

Constatamos que o carregamento do modelo de linguagem na memória (ou VRAM, quando disponível) gera lentidão acentuada no primeiro processamento de cada sessão (cold start). A primeira chamada após o Ollama subir pode levar de 10 a 30 segundos, enquanto as subsequentes são significativamente mais rápidas. Em produção, isso poderia ser mitigado com warmup automático (enviar uma requisição dummy ao LLM durante a inicialização do container).

### Escalabilidade

A principal limitação de escalabilidade está no LLM Gateway. Como o Ollama processa uma inferência por vez em modelos sem suporte a batching, o sistema fica limitado na capacidade simultânea de atender múltiplos usuários. Se dois usuários enviarem mensagens ao mesmo tempo, o segundo precisa esperar o primeiro terminar. Em um cenário de produção real, seria necessário avaliar soluções como vLLM ou TGI (Text Generation Inference) que suportam batching e paralelismo de inferência.

### Segurança

Identificamos três pontos de vulnerabilidade no MVP atual:

1. **Ausência de autenticação no API Gateway:** Qualquer pessoa com acesso à rede pode fazer requisições a todos os microsserviços via gateway. Em produção, seria indispensável adicionar autenticação (OAuth2/JWT) como filtro global no Spring Cloud Gateway.

2. **Credenciais expostas no compose.yaml:** Senhas do PostgreSQL (`root/root`), do RabbitMQ (`guest/guest`) e do Grafana (`admin/admin`) estão em texto puro no arquivo de configuração, que por sua vez está versionado no Git. Embora isso seja aceitável para desenvolvimento local, seria inadmissível em produção.

3. **Comunicação sem TLS entre serviços:** Toda a comunicação interna entre microsserviços ocorre via HTTP (sem TLS). Em um ambiente de produção, seria recomendável configurar mTLS (mutual TLS) entre os serviços, o que pode ser feito de forma transparente com uma service mesh como Istio.

### Disponibilidade

O serviço de memória é um ponto único de falha (SPOF) no fluxo principal. Se o PostgreSQL cair, o agente continua funcionando (graças ao circuit breaker que retorna histórico vazio), mas todas as sessões perdem o contexto. Em produção, o PostgreSQL deveria operar em modo de alta disponibilidade (réplica de leitura + failover automático).

---

## 9. Links de Vídeo


---

## 10. Conclusão

Ao longo do desenvolvimento desta plataforma, nosso grupo vivenciou na prática os desafios e benefícios de uma arquitetura baseada em microsserviços. A separação em sete serviços independentes nos permitiu trabalhar em paralelo — cada membro ficou responsável por um ou mais serviços — e nos forçou a pensar com cuidado nos contratos de comunicação entre as partes. Não foram poucas as vezes em que um serviço funcionava perfeitamente isolado, mas falhava ao integrar com os demais por causa de uma diferença sutil no formato do JSON ou no nome de um campo.

A implementação do ciclo agêntico (ReAct) foi, sem dúvida, a parte mais interessante do projeto. Ver o agente raciocinando, chamando ferramentas, analisando resultados e voltando ao LLM para continuar o raciocínio deu uma dimensão prática ao conceito de agentes autônomos que só tínhamos visto em artigos acadêmicos.

Os padrões de resiliência (circuit breaker) e comunicação assíncrona (RabbitMQ) mostraram seu valor em situações concretas: quando o Ollama travava ou demorava a responder, o circuit breaker evitava que todo o sistema ficasse pendurado esperando timeout. Da mesma forma, a publicação assíncrona de telemetria garantiu que métricas de monitoramento fossem coletadas sem impactar a experiência do usuário.

Se pudéssemos começar o projeto novamente, teríamos padronizado a stack tecnológica (todos os serviços em Java ou todos em Python) para simplificar a manutenção, e teríamos investido mais tempo em testes de integração automatizados. Também teríamos adotado rastreamento distribuído (OpenTelemetry) desde o início, pois a falta dele dificultou o debugging de problemas em produção.

No geral, o projeto cumpriu seu objetivo: construímos uma plataforma funcional de agentes conversacionais que demonstra na prática os principais padrões de arquitetura de microsserviços — comunicação síncrona e assíncrona, descoberta de serviços, roteamento via gateway, resiliência com circuit breaker, containerização com Docker Compose e observabilidade com Prometheus e Grafana.
