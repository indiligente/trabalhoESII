# Tool Registry

Microsserviço responsável por registrar e expor ferramentas que os agentes de IA podem invocar durante o ciclo agêntico (raciocínio → ação → observação).

## Índice

- [Responsabilidade](#responsabilidade)
- [Estrutura do projeto](#estrutura-do-projeto)
- [Explicação das camadas](#explicação-das-camadas)
- [API REST](#api-rest)
- [Ferramentas built-in](#ferramentas-built-in)
- [Como rodar](#como-rodar)
- [Como testar](#como-testar)

---

## Responsabilidade

O `tool-registry` age como um catálogo de ferramentas disponíveis na plataforma. Quando o `agent-service` precisa invocar uma ferramenta (ex: calcular um valor, consultar dados), ele:

1. Consulta `GET /tools` para descobrir quais ferramentas existem e seus parâmetros esperados
2. Chama `POST /tools/{name}/execute` com os parâmetros para obter o resultado

---

## Estrutura do projeto

```
tool-registry/
├── src/
│   ├── main/java/TF_ESII/tool_registry/
│   │   ├── ToolRegistryApplication.java     # ponto de entrada Spring Boot
│   │   ├── controller/
│   │   │   └── ToolController.java          # endpoints REST
│   │   ├── service/
│   │   │   ├── ToolService.java             # lógica de negócio (CRUD)
│   │   │   └── ToolExecutorService.java     # despacha execução para a tool certa
│   │   ├── model/
│   │   │   ├── Tool.java                    # entidade JPA (registro da ferramenta)
│   │   │   └── ToolInvocationRequest.java   # body do POST /execute
│   │   ├── repository/
│   │   │   └── ToolRepository.java          # acesso ao banco H2
│   │   ├── tools/
│   │   │   ├── CalculatorTool.java          # ferramenta: calculadora aritmética
│   │   │   └── DatabaseQueryTool.java       # ferramenta: consulta por chave
│   │   └── config/
│   │       └── BuiltInToolsInitializer.java # pré-carrega ferramentas no boot
│   ├── main/resources/
│   │   └── application.yaml                 # configurações (porta, H2, Eureka)
│   └── test/
│       └── ...                              # testes unitários por camada
├── Dockerfile
├── pom.xml
└── run-tests.sh / run-tests.bat
```

---

## Explicação das camadas

### `model/Tool.java`

Entidade JPA que representa uma ferramenta registrada no banco H2.

| Campo | Tipo | Descrição |
|---|---|---|
| `id` | Long | Gerado automaticamente |
| `name` | String | Nome único da ferramenta (ex: `calculator`) |
| `description` | String | Descrição legível por humanos e pelo agente |
| `parametersSchema` | String | JSON Schema dos parâmetros esperados |
| `builtIn` | boolean | Se `true`, não pode ser deletada via API |

---

### `model/ToolInvocationRequest.java`

Body tipado recebido no endpoint `POST /tools/{name}/execute`.

```json
{
  "params": {
    "expression": "10 / 2"
  }
}
```

Encapsula os parâmetros em um campo `params` para deixar a API explícita.

---

### `repository/ToolRepository.java`

Interface Spring Data JPA. Herda operações básicas (`save`, `findAll`, `delete`) e adiciona:

- `findByName(String name)` — busca ferramenta pelo nome
- `existsByName(String name)` — checa duplicatas antes de registrar

---

### `service/ToolService.java`

Contém a lógica de negócio. Todas as regras passam por aqui antes de chegar ao banco:

- **`listAll()`** — retorna todas as ferramentas
- **`findByName(name)`** — retorna `Optional<Tool>`
- **`register(tool)`** — valida que o nome não existe antes de salvar
- **`update(name, tool)`** — atualiza descrição e schema de uma tool existente
- **`delete(name)`** — bloqueia deleção de ferramentas `builtIn = true`
- **`execute(name, params)`** — valida que a ferramenta existe e delega ao `ToolExecutorService`

---

### `service/ToolExecutorService.java`

Despachante de execução. Recebe o nome da ferramenta e os parâmetros e encaminha para a classe concreta correta:

```
"calculator"     → CalculatorTool.execute(params)
"database-query" → DatabaseQueryTool.execute(params)
"echo"           → retorna { "echo": <mensagem> }
"datetime"       → retorna { "datetime": <ISO 8601> }
outro nome       → retorna { "status": "not_implemented" }
```

---

### `tools/CalculatorTool.java`

Avalia expressões aritméticas simples com dois operandos.

**Entrada:**
```json
{ "params": { "expression": "10 / 4" } }
```

**Saída:**
```json
{ "expression": "10 / 4", "result": 2.5 }
```

Operações suportadas: `+`, `-`, `*`, `/`. Divisão por zero retorna `{ "error": "..." }`.

---

### `tools/DatabaseQueryTool.java`

Simula consulta a um banco de dados por chave. Atualmente usa um `Map` em memória. Em produção, seria substituído por uma query real (JDBC, REST, etc).

**Entrada:**
```json
{ "params": { "key": "user:1" } }
```

**Saída (encontrado):**
```json
{ "found": true, "key": "user:1", "record": { "id": 1, "name": "Alice", "email": "alice@example.com" } }
```

**Saída (não encontrado):**
```json
{ "found": false, "key": "user:999" }
```

Chaves disponíveis no mock: `user:1`, `user:2`, `product:1`.

---

### `config/BuiltInToolsInitializer.java`

Roda automaticamente na inicialização da aplicação (`CommandLineRunner`). Registra as 4 ferramentas nativas no banco caso ainda não existam:

| Nome | Descrição |
|---|---|
| `calculator` | Avalia expressão aritmética |
| `database-query` | Consulta registro por chave |
| `echo` | Retorna a mensagem recebida |
| `datetime` | Retorna data/hora atual |

---

### `controller/ToolController.java`

Expõe a API REST. Todas as rotas partem de `/tools`.

---

## API REST

### Listar todas as ferramentas

```
GET /tools
```

**Resposta `200`:**
```json
[
  {
    "id": 1,
    "name": "calculator",
    "description": "Avalia expressão aritmética",
    "parametersSchema": "{ ... }",
    "builtIn": true
  }
]
```

---

### Buscar ferramenta por nome

```
GET /tools/{name}
```

**Resposta `200`** — ferramenta encontrada  
**Resposta `404`** — ferramenta não existe

---

### Registrar nova ferramenta

```
POST /tools
Content-Type: application/json

{
  "name": "minha-tool",
  "description": "Faz algo útil",
  "parametersSchema": "{ \"type\": \"object\", \"properties\": { \"input\": { \"type\": \"string\" } } }",
  "builtIn": false
}
```

**Resposta `201`** — criada  
**Resposta `409`** — nome já existe

---

### Atualizar ferramenta

```
PUT /tools/{name}
Content-Type: application/json

{
  "description": "Descrição atualizada",
  "parametersSchema": "{ ... }"
}
```

**Resposta `200`** — atualizada  
**Resposta `404`** — não encontrada

---

### Deletar ferramenta

```
DELETE /tools/{name}
```

**Resposta `204`** — deletada  
**Resposta `403`** — ferramenta é built-in  
**Resposta `404`** — não encontrada

---

### Executar ferramenta

```
POST /tools/{name}/execute
Content-Type: application/json

{
  "params": {
    "expression": "3 + 5"
  }
}
```

**Resposta `200`:**
```json
{ "expression": "3 + 5", "result": 8.0 }
```

**Resposta `404`** — ferramenta não registrada

---

## Ferramentas built-in

### `calculator`

```bash
POST /tools/calculator/execute
{ "params": { "expression": "100 / 4" } }
# → { "expression": "100 / 4", "result": 25.0 }
```

### `database-query`

```bash
POST /tools/database-query/execute
{ "params": { "key": "user:2" } }
# → { "found": true, "key": "user:2", "record": { "id": 2, "name": "Bob", ... } }
```

### `echo`

```bash
POST /tools/echo/execute
{ "params": { "message": "olá mundo" } }
# → { "echo": "olá mundo" }
```

### `datetime`

```bash
POST /tools/datetime/execute
{ "params": {} }
# → { "datetime": "2026-06-28T14:00:00Z" }
```

---

## Como rodar

### Pré-requisitos

- Java 21
- Maven (ou use o `mvnw` incluso)
- Eureka (name-server) rodando em `localhost:8761` — ou desabilite com a variável de ambiente abaixo

### Localmente (sem Docker)

```bash
# Com Eureka rodando
./mvnw spring-boot:run

# Sem Eureka (modo standalone)
./mvnw spring-boot:run -Dspring-boot.run.arguments="--eureka.client.enabled=false"
```

O serviço sobe em `http://localhost:8400`.  
Console H2: `http://localhost:8400/h2-console` (JDBC URL: `jdbc:h2:mem:toolregistrydb`)

### Via Docker Compose (plataforma completa)

```bash
# Na raiz do repositório
docker compose up tool-registry
```

---

## Como testar

Veja [`run-tests.sh`](run-tests.sh) (Linux/Mac) ou [`run-tests.bat`](run-tests.bat) (Windows) para rodar os testes com um único comando.

Para rodar manualmente:

```bash
./mvnw test
```

Os testes **não dependem** de nenhum serviço externo (sem Eureka, sem banco externo).
