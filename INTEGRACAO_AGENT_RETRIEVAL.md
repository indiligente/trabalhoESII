# Integração agent-service ↔ retrieval-service

Este documento registra os ajustes feitos para conectar o `agent-service` ao
`retrieval-service` de acordo com a arquitetura definida (Agent Service →
Retrieval Service, via REST, com descoberta pelo Eureka).

## Problema encontrado

Os dois serviços já tinham "esqueletos" de integração (`RetrievalServiceClient`
no agent-service e `RetrievalController` no retrieval-service), mas eles não
se conectavam de verdade:

| | agent-service esperava | retrieval-service expunha |
|---|---|---|
| Path | `POST /api/retrieval/search` | `GET /retrieval/search?pergunta=` |
| Corpo | JSON `{query, topK}` | `@RequestParam` |
| Resposta | `String` | `List<Document>` (Spring AI) |

Além disso, o `retrieval-service` não registrava no Eureka (faltava a
dependência `spring-cloud-starter-netflix-eureka-client`) e o
`application.yaml` estava com indentação quebrada — quase nenhuma propriedade
(`spring.application.name`, `eureka.client.service-url.defaultZone`,
`management.*`) realmente era aplicada, só o bloco `spring.ai.vectorstore.qdrant.*`
estava corretamente indentado.

## Ajustes feitos

### `retrieval-service`

- **`pom.xml`**: adicionadas as dependências `spring-cloud-starter-netflix-eureka-client`
  (para registrar no naming-server), `spring-boot-starter-web` (explícita) e
  `spring-boot-starter-actuator` (necessária para `/actuator/health`, usado no
  healthcheck do Docker Compose).
- **`application.yaml`**: reescrito com indentação correta. Agora
  `spring.application.name=retrieval-service` e
  `eureka.client.service-url.defaultZone` funcionam de fato. Foram removidos o
  bloco de Spring Cloud Gateway (`spring.cloud.gateway.*`) e o bloco
  `resilience4j.*`, que eram sobras de outro serviço e não tinham dependência
  correspondente no `pom.xml`. Foi adicionada a configuração
  `spring.ai.ollama.base-url` e `spring.ai.ollama.embedding.model`, necessária
  para o `VectorStore` conseguir gerar embeddings via Ollama antes de
  gravar/consultar no Qdrant. Corrigido também `spring.ai.vectorstore.qdrant.api-key`
  (a chave `api-token`, que existia antes, não existe na classe de propriedades
  do Spring AI — foi confirmado inspecionando o jar `spring-ai-autoconfigure-vector-store-qdrant`).
- **`RetrievalController.java`**: novo contrato sob o prefixo `/api/retrieval`
  (alinhado ao `agent-service`):
  - `POST /api/retrieval/documents` — indexa um documento (`{content, metadata}`).
  - `POST /api/retrieval/search` — busca semântica (`{query, topK}` →
    `{results: [{id, content, score, metadata}]}`).
  - `GET /api/retrieval/health` — health check simples (além do `/actuator/health`).
- **`Retrieval.java`**: `buscarDocumentos` agora recebe `topK` como parâmetro
  em vez de usar um valor fixo (3).
- Novos DTOs em `TF_ESII.TF.retrieval_service.dto`: `RetrievalSearchRequest`,
  `RetrievalSearchResult`, `RetrievalSearchResponse`, `RetrievalIndexRequest` —
  para não expor o tipo interno `org.springframework.ai.document.Document`
  diretamente na API pública.

### `agent-service`

- **`feign/RetrievalServiceClient.java`**: atualizado para
  `POST /api/retrieval/search`, usando os novos DTOs (`RetrievalSearchRequest`
  → `RetrievalSearchResponse`), casando exatamente com o novo contrato do
  retrieval-service.
- Novos DTOs espelhados em `TF_ESII.TF.DTO.retrieval` (os dois serviços são
  módulos Maven independentes, então não compartilham classes Java).
- **`agent/AgentTools.java`**: `buscarNaBaseDeConhecimento` agora monta a
  requisição tipada, formata os resultados retornados (conteúdo + score) em
  texto legível para o LLM, e captura falhas do Feign (serviço fora do ar,
  timeout) devolvendo uma mensagem amigável em vez de propagar uma exceção que
  derrubaria a resposta do chat — mesmo padrão já usado para o
  `memory-service`.
- **`application.yaml`**: adicionado um timeout específico para o cliente
  Feign do `retrieval-service` (`readTimeout: 60000`), já que uma busca
  semântica com geração de embedding pode ser mais lenta que uma chamada
  comum.

### `compose.yaml` (raiz)

- Novo serviço **`qdrant`** (imagem `qdrant/qdrant`, portas `6333`/`6334`,
  volume `qdrant_data`).
- Novo serviço **`ollama-pull-embed`**, que baixa o modelo de embedding
  `nomic-embed-text` no Ollama (o `qwen3.5` já usado pelo `llm-gateway` é um
  modelo de chat, não de embedding).
- Novo serviço **`retrieval-service`** (porta `8766`), com `depends_on`
  aguardando `naming-server`, `qdrant` (saudável) e `ollama-pull-embed`
  (concluído).
- **`agent-service`** agora também depende de `retrieval-service` estar
  saudável antes de subir.

> O `agent-service/docker-compose.yml` (compose local, usado para testar o
> agent-service isolado com Eureka desabilitado) não foi alterado — ele não
> inclui `qdrant`/`retrieval-service`, então a ferramenta
> `buscarNaBaseDeConhecimento` retornará a mensagem de fallback
> ("Base de conhecimento indisponível no momento.") quando usado sozinho, o
> que é o comportamento esperado nesse cenário isolado.

### Correção: `qdrant` ficava `unhealthy` e travava o `docker compose up`

O healthcheck original do `qdrant` usava `wget -qO- http://localhost:6333/readyz`,
copiado do padrão usado nos outros serviços (que rodam em imagens Alpine, onde
o `wget` do busybox está sempre disponível). A imagem oficial `qdrant/qdrant`
é baseada em Debian **sem** `wget`/`curl` instalados — confirmado rodando
`docker run --entrypoint sh qdrant/qdrant:latest -c "which wget; which curl; which bash"`,
que só retorna `bash`. Isso fazia o healthcheck falhar sempre (comando não
encontrado), o container ficar marcado como `unhealthy` para sempre, e o
Compose recusar subir qualquer serviço que dependesse dele
(`dependency failed to start: container ...qdrant-1 is unhealthy`).

**Correção aplicada** em `compose.yaml` e `compose.retrieval-test.yaml`: troca
do healthcheck por uma checagem de porta TCP usando o `/dev/tcp` do bash
(que existe na imagem):
```yaml
test: ["CMD-SHELL", "bash -c 'cat < /dev/null > /dev/tcp/127.0.0.1/6333' || exit 1"]
```
Validado manualmente subindo o container e observando o status mudar de
`starting` para `healthy`.

### Correção: `retrieval-service` também ficava `unhealthy` (dependência do Redis não usada)

Com o `qdrant` já saudável, o `retrieval-service` continuava subindo como
`unhealthy`. Os logs mostravam tentativas contínuas de conexão com
`localhost:6379` falhando (`RedisConnectionFailureException`). A causa era a
dependência `spring-boot-starter-session-data-redis` no `pom.xml`: ela ativa
automaticamente um indicador de saúde do Spring Session/Redis dentro do
`/actuator/health`, mesmo sem nenhum código do serviço usar sessões HTTP ou
Redis (confirmado por busca no código — nenhuma referência a `Redis`/`Session`
em `retrieval-service/src`). Sem um container Redis no ar (nem no
`compose.yaml`, nem no `compose.retrieval-test.yaml`), esse indicador ficava
`DOWN` para sempre e arrastava o health geral do serviço junto — exatamente o
ponto em aberto que o `TutorialRetrieval.md` já sinalizava na seção 9.

**Correção aplicada**: removida a dependência `spring-boot-starter-session-data-redis`
(e seu par de teste) do `pom.xml`, já que não é usada. Como o par de teste
removido era, sem querer, a única fonte de JUnit/Spring Test no classpath
(o projeto não declarava `spring-boot-starter-test`), a compilação dos testes
(`TfApplicationTests.java`) quebrou durante o build da imagem Docker (o
`Dockerfile` roda `mvnw clean install -DskipTests`, e `-DskipTests` pula a
**execução** dos testes mas não a **compilação** deles). Corrigido adicionando
a dependência correta e padrão para testes Spring Boot,
`spring-boot-starter-test` (escopo `test`), mesma usada no `agent-service`.

### Testar agent-service + retrieval-service sem precisar do LLM

Como você notou, no diagrama de arquitetura o `agent-service` chama o
`retrieval-service` e o `llm-gateway` em ramos separados — a busca semântica
não depende do LLM. Só que, no ciclo ReAct atual, a ferramenta
`buscarNaBaseDeConhecimento` só é acionada se o LLM decidir chamá-la durante
`/api/agent/chat`; sem o `llm-gateway` no ar, essa rota falha antes de chegar
lá. Para permitir testar a comunicação `agent-service` ↔ `retrieval-service`
isoladamente, sem subir `llm-gateway`/baixar o modelo de chat, foram
adicionados:

- **`GET /api/agent/debug/retrieval?query=...`** em `AgentController` —
  chama `agentTools.buscarNaBaseDeConhecimento(query)` diretamente, pulando o
  ciclo ReAct e o LLM. Exercita exatamente o mesmo caminho (Feign → Eureka →
  `retrieval-service`) que seria usado durante um chat normal.
- **`compose.retrieval-test.yaml`** — stack reduzida com apenas
  `naming-server`, `rabbitmq`, `qdrant`, `ollama` + `ollama-pull-embed`,
  `retrieval-service` e `agent-service` (sem `llm-gateway`/`ollama-pull` do
  modelo de chat `qwen3.5`). Suba com:
  ```powershell
  docker compose -f compose.retrieval-test.yaml up --build
  ```

## Novo fluxo de comunicação

```
agent-service :8765
    │  Feign → descobre "retrieval-service" via Eureka (:8761)
    │  POST /api/retrieval/search  { "query": "...", "topK": 4 }
    ▼
retrieval-service :8766
    │  gera embedding da consulta via Ollama (nomic-embed-text)
    │  busca por similaridade no Qdrant (:6334)
    ▼
Qdrant  →  retorna os documentos mais relevantes
    │
    └─ resposta sobe até o agent-service, que formata e devolve ao LLM
```

## Como validar

Veja a seção **4.3 — Retrieval-service (RAG)** do [`COMO_EXECUTAR.md`](COMO_EXECUTAR.md)
para o passo a passo completo (indexar um documento, buscar diretamente no
retrieval-service, testar via `/api/agent/debug/retrieval` sem o LLM, e
confirmar que o agent-service usa a ferramenta `buscarNaBaseDeConhecimento`
no chat completo).

## Validação executada

Toda a stack reduzida (`compose.retrieval-test.yaml`) foi construída e subida
localmente para validar as correções acima. Resultado:

```
$ docker compose -f compose.retrieval-test.yaml ps
naming-server        healthy
rabbitmq             healthy
ollama               healthy
qdrant               healthy
retrieval-service    healthy
agent-service        healthy (Up)
```

Fluxo completo testado com sucesso:
1. `POST /api/retrieval/documents` → documento indexado no Qdrant (embedding gerado via Ollama).
2. `POST /api/retrieval/search` (direto no retrieval-service) → retornou o documento indexado com `score=0.84`.
3. `GET /api/agent/debug/retrieval?query=...` (no agent-service) → mesmo resultado, confirmando que o Feign client descobre o `retrieval-service` via Eureka e recebe a resposta corretamente:
   ```json
   {"query":"Qual e a capital do Brasil?","resultado":"- A capital do Brasil e Brasilia, fundada em 21 de abril de 1960. (score=0.8410111665725708)"}
   ```

> **Nota sobre acentuação em testes via terminal:** ao testar com `curl` em
> Git Bash/PowerShell, caracteres acentuados digitados direto na linha de
> comando podem chegar corrompidos ao servidor (`Invalid UTF-8 middle byte`),
> dependendo da codificação do terminal. Se isso acontecer, salve o corpo da
> requisição em um arquivo `.json` (UTF-8) e use `curl --data-binary @arquivo.json`
> em vez de `-d '...'` inline.
