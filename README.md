# Plataforma de Agentes Conversacionais com Microsservicos

Este repositorio contem o codigo-fonte, os arquivos de configuracao e as instrucoes para executar localmente e testar a plataforma desenvolvida para o Trabalho Final de Engenharia de Software II.

A solucao implementa um MVP de uma plataforma de agentes conversacionais com execucao local via Docker Compose, sem dependencia de recursos de nuvem. O agente segue o ciclo ReAct (raciocinio -> acao -> observacao), usando um LLM local via Ollama, ferramentas registradas no Tool Registry, memoria conversacional, busca semantica e observabilidade basica.

## Estrutura do repositorio

| Caminho | Conteudo |
| --- | --- |
| `agent-service/` | Microsservico Spring Boot que recebe as mensagens do usuario, orquestra o ciclo agentico, chama LLM, ferramentas, memoria e retrieval. |
| `llm-gateway/` | Microsservico FastAPI que centraliza o acesso ao Ollama e aplica circuit breaker para chamadas ao modelo local. |
| `service-memory/` | Microsservico FastAPI que persiste historico de conversa no PostgreSQL e usa Redis como cache. |
| `retrieval-service/` | Microsservico Spring Boot responsavel por RAG, embeddings locais e busca vetorial no Qdrant. |
| `tool-registry/` | Microsservico Spring Boot que registra, lista e executa ferramentas disponiveis para o agente. |
| `api-gateway/` | Gateway Spring Cloud que centraliza o acesso externo aos microsservicos. |
| `naming-server/` | Eureka Server usado para descoberta de servicos. |
| `observability/` | Configuracoes do Prometheus e provisionamento do Grafana. |
| `compose.yaml` | Orquestracao local completa com microsservicos, infraestrutura, observabilidade e Ollama. |
| `mise.toml` | Versao de Java usada no projeto (`21.0.0`). |
| `Relatório Técnico - Trabalho ESII.pdf` | Relatorio tecnico da arquitetura, decisoes e trade-offs. |
| `t1_2026_1.pdf` | Enunciado do trabalho. |

## Servicos e portas

| Servico | Porta local | Observacao |
| --- | ---: | --- |
| API Gateway | `8080` | Ponto de entrada principal da plataforma. |
| Agent Service | `8765` | API do agente conversacional. |
| LLM Gateway | `8767` | Proxy para o Ollama. |
| Retrieval Service | `8766` | Indexacao e busca semantica. |
| Tool Registry | `8082` -> container `8400` | Registro e execucao de ferramentas. |
| Memory Service | `8000` | Historico de conversacao. |
| Eureka Naming Server | `8761` | Descoberta de servicos. |
| Ollama | `11434` | Modelos locais `qwen2.5:0.5b` e `nomic-embed-text`. |
| RabbitMQ Management | `15672` | Usuario `guest`, senha `guest`. |
| Prometheus | `9090` | Coleta de metricas. |
| Grafana | `3000` | Usuario `admin`, senha `admin`. |
| Qdrant | `6333`, `6334` | Banco vetorial. |
| PostgreSQL | `5432` | Usuario `root`, senha `root`, banco `postgres`. |

## Pre-requisitos

- Docker e Docker Compose instalados.
- Git instalado.
- Java 21 instalado para rodar testes Maven fora do Docker.
- `curl` para executar os testes manuais por terminal.
- Recomendado: pelo menos 16 GB de RAM, pois o ambiente sobe varios microsservicos, bancos, RabbitMQ, observabilidade e modelos locais do Ollama.

## Como iniciar localmente

1. Clone o repositorio e acesse a pasta raiz:

```bash
git clone <url-do-repositorio>
cd trabalhoESII
```

2. Suba toda a plataforma:

```bash
docker compose up -d --build
```

Na primeira execucao, o Docker baixa as imagens e o Ollama baixa automaticamente os modelos `qwen2.5:0.5b` e `nomic-embed-text`. Esse processo pode levar alguns minutos.

3. Verifique se os containers estao em execucao:

```bash
docker compose ps
```

4. Acompanhe os logs, se necessario:

```bash
docker compose logs -f
```

Para ver os logs de um servico especifico:

```bash
docker compose logs -f agent-service
docker compose logs -f llm-gateway
```

5. Acesse os paineis principais:

- API Gateway: `http://localhost:8080`
- Eureka: `http://localhost:8761`
- Grafana: `http://localhost:3000` (`admin` / `admin`)
- Prometheus: `http://localhost:9090`
- RabbitMQ: `http://localhost:15672` (`guest` / `guest`)

## Como parar a plataforma

Para parar os containers sem apagar dados persistidos:

```bash
docker compose down
```

Para parar e remover tambem os volumes locais de PostgreSQL, Qdrant, Ollama e Grafana:

```bash
docker compose down -v
```

Use `docker compose down -v` apenas quando quiser reiniciar o ambiente do zero, pois os modelos do Ollama e os dados dos bancos serao baixados/criados novamente.

## Configuracao local

A configuracao principal para execucao local esta no arquivo `compose.yaml`. Ele define:

- build dos sete microsservicos;
- variaveis de ambiente de cada servico;
- rede Docker `platform_network`;
- volumes persistentes;
- healthchecks;
- dependencias de inicializacao entre containers;
- infraestrutura local: PostgreSQL, Redis, RabbitMQ, Qdrant, Ollama, Prometheus e Grafana.

Os servicos Java tambem possuem configuracoes em `src/main/resources/application.yaml`. Os servicos Python usam variaveis de ambiente injetadas pelo Docker Compose.

Para a execucao padrao via Docker Compose, **nao e necessario criar arquivos `.env`**. O Docker ja cuida de todo o roteamento e conexoes internas.

### Construindo os arquivos `.env` (apenas para desenvolvimento local na IDE)

Se voce quiser executar um servico especifico fora do Docker (por exemplo, diretamente na sua IDE para debugar o codigo), voce precisara definir variaveis de ambiente para que ele se conecte a infraestrutura (banco de dados, mensageria, etc).

Para facilitar, disponibilizamos um arquivo chamado **`.env.example`** na raiz do repositorio, que agrupa os valores de referencia para cada um dos servicos.

**Como construir seu `.env`:**
1. Abra o arquivo `.env.example`.
2. Encontre a secao correspondente ao servico que deseja rodar (por exemplo, `agent-service`).
3. Crie um arquivo com o nome `.env` **dentro da pasta do servico** (ex: `agent-service/.env`).
4. Copie as variaveis do arquivo de exemplo para o seu novo arquivo `.env`. Os enderecos ja estao configurados apontando para `localhost`.
5. Se voce precisar, pode subir apenas os containers de infraestrutura (`docker compose up -d postgres redis rabbitmq ollama qdrant`) e iniciar sua aplicacao na IDE. Ela conectara nesses servicos locais.

## Testes automatizados

Os testes automatizados estao principalmente nos microsservicos Java. Para executa-los fora do Docker, tenha o Java 21 disponivel no `PATH`.

### Tool Registry

O `tool-registry` possui script proprio de testes:

```bash
cd tool-registry
./run-tests.sh
```

Para rodar uma classe especifica:

```bash
cd tool-registry
./run-tests.sh ToolServiceTest
```

### Outros servicos Java

Execute o Maven Wrapper dentro de cada servico:

```bash
cd agent-service
./mvnw test
```

```bash
cd retrieval-service
./mvnw test
```

```bash
cd naming-server
./mvnw test
```

```bash
cd api-gateway
./mvnw test
```

### Rodar todos os testes Java em sequencia

A partir da raiz do repositorio:

```bash
(cd tool-registry && ./run-tests.sh)
(cd agent-service && ./mvnw test)
(cd retrieval-service && ./mvnw test)
(cd naming-server && ./mvnw test)
(cd api-gateway && ./mvnw test)
```

## Testes manuais da plataforma em execucao

Com o ambiente iniciado por `docker compose up -d --build`, os seguintes comandos validam os fluxos principais.

### Health checks

```bash
curl http://localhost:8080/agent-service/api/agent/health
curl http://localhost:8080/llm-gateway/health
curl http://localhost:8080/service-memory/health
curl http://localhost:8080/retrieval-service/api/retrieval/health
```

### Listar ferramentas registradas

```bash
curl http://localhost:8080/tool-registry/tools
```

### Executar uma ferramenta

```bash
curl -X POST http://localhost:8080/tool-registry/tools/calculator/execute \
  -H "Content-Type: application/json" \
  -d '{"params":{"expression":"2+2"}}'
```

### Indexar documento no Retrieval Service

```bash
curl -X POST http://localhost:8080/retrieval-service/api/retrieval/documents \
  -H "Content-Type: application/json" \
  -d '{"content":"A plataforma usa Docker Compose, Eureka, RabbitMQ, Ollama, PostgreSQL, Redis e Qdrant.","metadata":{"origem":"teste-manual"}}'
```

### Buscar documento indexado

```bash
curl -X POST http://localhost:8080/retrieval-service/api/retrieval/search \
  -H "Content-Type: application/json" \
  -d '{"query":"Quais tecnologias a plataforma usa?","topK":3}'
```

### Enviar mensagem ao agente

```bash
curl -X POST http://localhost:8080/agent-service/api/agent/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"Resuma em uma frase o objetivo desta plataforma.","sessionId":"sessao-teste"}'
```

### Validar memoria da conversa

```bash
curl http://localhost:8080/service-memory/api/memory/sessao-teste
```

## Observabilidade

O projeto usa Prometheus e Grafana para observabilidade basica.

- Prometheus coleta metricas dos servicos Java via `/actuator/prometheus`.
- Grafana ja sobe com o Prometheus provisionado como datasource.
- O Agent Service publica eventos de telemetria no RabbitMQ durante o ciclo agentico.

Acesse:

- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3000`
- RabbitMQ: `http://localhost:15672`

## Solucao de problemas

Se algum servico nao responder imediatamente, aguarde alguns minutos e confira:

```bash
docker compose ps
docker compose logs -f <nome-do-servico>
```

Se o LLM demorar na primeira requisicao, verifique se os modelos foram baixados:

```bash
docker compose logs ollama-pull-llm
docker compose logs ollama-pull-embed
```

Se quiser reconstruir todo o ambiente:

```bash
docker compose down
docker compose up -d --build
```

Se quiser limpar dados e volumes:

```bash
docker compose down -v
docker compose up -d --build
```

## Relatorio tecnico

As decisoes de arquitetura, trade-offs, dificuldades, oportunidades de melhoria e discussao sobre evolucao para Kubernetes estao documentadas no arquivo `Relatório Técnico - Trabalho ESII.pdf`.
