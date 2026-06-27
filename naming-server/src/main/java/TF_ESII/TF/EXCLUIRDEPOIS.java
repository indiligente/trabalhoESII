package TF_ESII.TF;

// ============================================================
//  EXCLUIRDEPOIS.java  —  Arquivo de exemplo educacional
//  NÃO altere nenhum arquivo existente do projeto.
//  EXCLUA este arquivo após ler e implementar nos arquivos certos.
//
//  Este arquivo mostra como estruturar um Agent Service completo
//  usando as dependências que já existem no pom.xml:
//    - spring-ai-ollama-spring-boot-starter     (LLM local via Ollama)
//    - spring-ai-qdrant-store-spring-boot-starter (vector store RAG)
//    - spring-ai-advisors-vector-store           (advisor de RAG)
//    - spring-session-data-redis                 (histórico de sessão)
//    - resilience4j                              (circuit breaker)
//
//  ESTRUTURA DE ARQUIVOS A CRIAR NO PROJETO:
//  ─────────────────────────────────────────
//  src/main/java/TF_ESII/TF/
//  ├── dto/
//  │   ├── AgentRequest.java         ← Bloco 1
//  │   └── AgentResponse.java        ← Bloco 1
//  ├── config/
//  │   └── AgentConfig.java          ← Bloco 2
//  ├── agent/
//  │   ├── AgentTools.java           ← Bloco 3
//  │   ├── AgentService.java         ← Bloco 4  (CORE: ciclo agêntico)
//  │   └── AgentController.java      ← Bloco 5
//  └── application.yaml              ← Bloco 6  (adições necessárias)
// ============================================================

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@SuppressWarnings("all")
public class EXCLUIRDEPOIS {

    // ================================================================
    // BLOCO 1 — DTOs
    // Crie como arquivos separados em: src/main/java/TF_ESII/TF/dto/
    // ================================================================

    /**
     * AgentRequest.java
     *
     * Representa a entrada do usuário no sistema.
     * O sessionId identifica a conversa — se não informado,
     * uma nova sessão é criada automaticamente.
     */
    record AgentRequest(
        String message,   // texto enviado pelo usuário
        String sessionId  // ID da sessão (multi-turn conversation)
    ) {
        // Garante que sempre existe um sessionId
        public AgentRequest {
            if (sessionId == null || sessionId.isBlank()) {
                sessionId = UUID.randomUUID().toString();
            }
        }
    }

    /**
     * AgentResponse.java
     *
     * Resposta final do agente após completar o ciclo agêntico.
     * Inclui metadados úteis para debug: ferramentas usadas e
     * quantas iterações do loop foram necessárias.
     */
    record AgentResponse(
        String sessionId,
        String answer,
        List<String> toolsUsed,
        int iterations
    ) {}


    // ================================================================
    // BLOCO 2 — CONFIGURAÇÃO
    // Crie como: src/main/java/TF_ESII/TF/config/AgentConfig.java
    // ================================================================

    /**
     * AgentConfig.java
     *
     * Define os beans do Spring AI: ChatClient configurado com
     * o prompt de sistema padrão do agente.
     *
     * O ChatClient é o ponto de entrada de alto nível para o Ollama.
     * A instância de VectorStore (Qdrant) é criada automaticamente
     * pelo auto-configure do spring-ai-qdrant-store-spring-boot-starter.
     */
    @Configuration
    static class AgentConfig {

        @Bean
        public ChatClient chatClient(ChatModel chatModel) {
            return ChatClient.builder(chatModel)
                .defaultSystem("""
                    Você é um assistente inteligente e prestativo.

                    Você tem acesso a ferramentas que pode usar para buscar informações.
                    SEMPRE verifique a base de conhecimento antes de responder perguntas
                    sobre o sistema ou seus dados.

                    Processo que você DEVE seguir:
                    1. Analise a pergunta do usuário.
                    2. Se precisar de informações externas, use uma ferramenta.
                    3. Use os resultados da ferramenta para formular a resposta.
                    4. Responda de forma clara, objetiva e em português.

                    Quando não souber algo, diga claramente ao invés de inventar.
                    """)
                .build();
        }
    }


    // ================================================================
    // BLOCO 3 — FERRAMENTAS (TOOLS)
    // Crie como: src/main/java/TF_ESII/TF/agent/AgentTools.java
    //
    // As ferramentas são os "braços" do agente: ações concretas
    // que ele pode executar quando o LLM decide que precisa delas.
    // Cada método anotado com @Tool é uma ação disponível.
    // ================================================================

    @Component
    static class AgentTools {

        private final VectorStore vectorStore;

        AgentTools(VectorStore vectorStore) {
            this.vectorStore = vectorStore;
        }

        /**
         * Ferramenta de RAG (Retrieval-Augmented Generation).
         * O agente usa isso para buscar informações no Qdrant
         * antes de responder perguntas sobre o domínio do sistema.
         */
        @Tool(description = "Busca informações relevantes na base de conhecimento do sistema. "
            + "Use sempre que precisar de dados específicos do domínio.")
        public String buscarNaBaseDeConhecimento(
            @ToolParam(description = "A consulta de busca em linguagem natural") String consulta
        ) {
            var resultados = vectorStore.similaritySearch(
                SearchRequest.builder()
                    .query(consulta)
                    .topK(4)          // retorna os 4 documentos mais similares
                    .similarityThreshold(0.7) // mínimo de 70% de similaridade
                    .build()
            );

            if (resultados.isEmpty()) {
                return "Nenhuma informação encontrada na base de conhecimento para: " + consulta;
            }

            return resultados.stream()
                .map(doc -> "Fonte: " + doc.getMetadata().getOrDefault("source", "desconhecida")
                          + "\nConteúdo: " + doc.getText())
                .collect(Collectors.joining("\n\n---\n\n"));
        }

        /**
         * Ferramenta utilitária: data e hora atual.
         * Exemplo simples de tool sem parâmetros.
         */
        @Tool(description = "Retorna a data e hora atual do servidor")
        public String obterDataHoraAtual() {
            return LocalDateTime.now().toString();
        }

        /**
         * Adicione aqui outras ferramentas conforme necessidade:
         *
         *   @Tool(description = "Consulta status de um serviço pelo nome")
         *   public String consultarStatusServico(
         *       @ToolParam(description = "Nome do serviço") String nomeServico
         *   ) { ... }
         *
         *   @Tool(description = "Registra um evento no sistema")
         *   public String registrarEvento(
         *       @ToolParam(description = "Descrição do evento") String descricao
         *   ) { ... }
         */
    }


    // ================================================================
    // BLOCO 4 — SERVIÇO AGÊNTICO (CORE DO SISTEMA)
    // Crie como: src/main/java/TF_ESII/TF/agent/AgentService.java
    //
    // Este é o bloco mais importante: implementa o ciclo agêntico.
    //
    // São mostradas DUAS abordagens:
    //   A) Automática via Spring AI (ChatClient gerencia o loop)
    //   B) Manual com loop ReAct explícito (mais controle/visibilidade)
    //
    // RECOMENDAÇÃO: comece com a Abordagem A. Use a B se precisar
    // interceptar cada iteração para logging, métricas ou lógica customizada.
    // ================================================================

    // ──────────────────────────────────────────────────────────────
    // ABORDAGEM A — Spring AI gerencia o ciclo automaticamente
    //
    // Como funciona internamente (o framework faz isso por você):
    //   1. Envia mensagem ao Ollama com a lista de tools disponíveis
    //   2. Ollama decide: "preciso de uma tool" → retorna ToolCall
    //   3. Spring AI executa a tool e devolve o resultado ao Ollama
    //   4. Ollama raciocina com o resultado e decide o próximo passo
    //   5. Repete até Ollama dar uma resposta de texto final
    //
    // Vantagem: menos código, gerenciado pela framework.
    // ──────────────────────────────────────────────────────────────
    @Service
    static class AgentServiceAuto {

        private final ChatClient chatClient;
        private final VectorStore vectorStore;
        private final AgentTools agentTools;

        // Armazena histórico de cada sessão em memória
        // Em produção: substitua por RedisSessionRepository ou similar
        private final Map<String, InMemoryChatMemory> memoriasPorSessao = new ConcurrentHashMap<>();

        AgentServiceAuto(ChatClient chatClient, VectorStore vectorStore, AgentTools agentTools) {
            this.chatClient = chatClient;
            this.vectorStore = vectorStore;
            this.agentTools = agentTools;
        }

        public AgentResponse processar(AgentRequest request) {
            // Recupera ou cria memória da sessão
            var memoria = memoriasPorSessao.computeIfAbsent(
                request.sessionId(),
                id -> new InMemoryChatMemory()
            );

            // O ChatClient lida com o loop ReAct completo:
            //   MessageChatMemoryAdvisor → injeta histórico da conversa
            //   QuestionAnswerAdvisor    → busca no Qdrant automaticamente
            //   .tools(agentTools)       → disponibiliza as ferramentas
            String resposta = chatClient.prompt()
                .user(request.message())
                .advisors(
                    new MessageChatMemoryAdvisor(memoria),    // memória da sessão
                    new QuestionAnswerAdvisor(vectorStore)    // RAG automático com Qdrant
                )
                .tools(agentTools)  // expõe os @Tool methods ao LLM
                .call()
                .content();

            return new AgentResponse(request.sessionId(), resposta, List.of("auto"), 1);
        }
    }


    // ──────────────────────────────────────────────────────────────
    // ABORDAGEM B — Loop ReAct manual (Reasoning → Action → Observation)
    //
    // Fluxo explícito de cada iteração do ciclo agêntico:
    //
    //   ┌─────────────────────────────────────────────────┐
    //   │  [Usuário] envia mensagem                       │
    //   └────────────────────┬────────────────────────────┘
    //                        ▼
    //   ┌─────────────────────────────────────────────────┐
    //   │  REASONING: LLM analisa histórico + mensagem    │
    //   │  e decide se precisa de uma ferramenta          │
    //   └────────────────────┬────────────────────────────┘
    //                        │
    //            ┌───────────┴───────────┐
    //            │ tem ToolCall?         │ não tem ToolCall?
    //            ▼                       ▼
    //   ┌─────────────────┐    ┌─────────────────────────┐
    //   │ ACTION:         │    │ Resposta final → retorna │
    //   │ executa a tool  │    │ ao usuário               │
    //   └────────┬────────┘    └─────────────────────────┘
    //            ▼
    //   ┌─────────────────┐
    //   │ OBSERVATION:    │
    //   │ resultado da    │
    //   │ tool → histórico│
    //   └────────┬────────┘
    //            │
    //            └──────► volta para REASONING (próxima iteração)
    //
    // Vantagem: controle total, fácil adicionar logging por iteração,
    //           métricas de quantas tools foram chamadas, timeout, etc.
    // ──────────────────────────────────────────────────────────────
    @Service
    static class AgentService {

        private static final int MAX_ITERACOES = 10;

        private static final String PROMPT_SISTEMA = """
            Você é um assistente inteligente com acesso a ferramentas.

            Processo obrigatório:
            1. RAZÃO: analise o que o usuário precisa.
            2. AÇÃO: se precisar de dados externos, chame uma ferramenta.
            3. OBSERVAÇÃO: use o resultado da ferramenta para continuar.
            4. Repita até ter informação suficiente para responder.
            5. RESPOSTA FINAL: responda em português de forma clara e objetiva.

            Não invente informações. Se não souber, diga claramente.
            """;

        private final ChatModel chatModel;
        private final AgentTools agentTools;

        // Map de sessionId → lista de mensagens do histórico
        private final Map<String, List<Message>> historicoSessoes = new ConcurrentHashMap<>();

        AgentService(ChatModel chatModel, AgentTools agentTools) {
            this.chatModel = chatModel;
            this.agentTools = agentTools;
        }

        public AgentResponse processar(AgentRequest request) {
            // Recupera ou inicializa o histórico da sessão com o system prompt
            List<Message> historico = historicoSessoes.computeIfAbsent(
                request.sessionId(),
                id -> new ArrayList<>(List.of(new SystemMessage(PROMPT_SISTEMA)))
            );

            // Adiciona a mensagem do usuário ao histórico
            historico.add(new UserMessage(request.message()));

            List<String> ferramentasUsadas = new ArrayList<>();
            int iteracao = 0;

            // ======================================================
            // CICLO AGÊNTICO PRINCIPAL
            // Reasoning → Action → Observation → (repete)
            // ======================================================
            while (iteracao < MAX_ITERACOES) {
                iteracao++;

                // ── REASONING ──────────────────────────────────────
                // Envia todo o histórico ao LLM com as tools disponíveis.
                // O LLM raciocina e decide: resposta final OU usar tool.
                ChatResponse respostaLLM = chatModel.call(
                    new Prompt(
                        historico,
                        ToolCallingChatOptions.builder()
                            .toolObjects(List.of(agentTools)) // tools disponíveis
                            .build()
                    )
                );

                AssistantMessage mensagemDoAssistente = respostaLLM.getResult().getOutput();

                // Adiciona a resposta do LLM ao histórico (sempre)
                historico.add(mensagemDoAssistente);

                // ── VERIFICAÇÃO: Resposta final ou ação? ───────────
                if (!mensagemDoAssistente.hasToolCalls()) {
                    // O LLM não pediu nenhuma tool → ciclo encerrado → resposta final
                    return new AgentResponse(
                        request.sessionId(),
                        mensagemDoAssistente.getText(),
                        ferramentasUsadas,
                        iteracao
                    );
                }

                // ── ACTION ─────────────────────────────────────────
                // O LLM pediu uma ou mais ferramentas. Executa cada uma.
                List<ToolResponseMessage.ToolResponse> resultadosDasTools = new ArrayList<>();

                for (AssistantMessage.ToolCall toolCall : mensagemDoAssistente.getToolCalls()) {
                    String nomeDaTool = toolCall.name();
                    ferramentasUsadas.add(nomeDaTool);

                    // ── OBSERVATION ────────────────────────────────
                    // Executa a ferramenta e coleta o resultado (observação).
                    String resultado = executarFerramenta(nomeDaTool, toolCall.arguments());

                    resultadosDasTools.add(new ToolResponseMessage.ToolResponse(
                        toolCall.id(),
                        nomeDaTool,
                        resultado
                    ));
                }

                // Adiciona os resultados das ferramentas ao histórico.
                // Na próxima iteração, o LLM vai ler esses resultados
                // e raciocinar com eles (Observation → Reasoning).
                historico.add(new ToolResponseMessage(resultadosDasTools));

                // ── Próxima iteração do ciclo ──────────────────────
            }

            // Limite de iterações atingido (evita loops infinitos)
            return new AgentResponse(
                request.sessionId(),
                "Não foi possível completar a tarefa no número máximo de iterações. "
                    + "Tente reformular sua pergunta.",
                ferramentasUsadas,
                iteracao
            );
        }

        /**
         * Despacha a chamada de tool pelo nome.
         * Adicione um case aqui para cada @Tool definido em AgentTools.
         *
         * Em produção, considere usar reflexão ou um registry de tools
         * em vez do switch manual, para não precisar alterar este método
         * cada vez que uma nova tool for adicionada.
         */
        private String executarFerramenta(String nome, String argumentosJson) {
            // Para parsear os argumentos JSON use ObjectMapper:
            //   Map<String, Object> args = objectMapper.readValue(argumentosJson, Map.class);
            //   String consulta = (String) args.get("consulta");

            return switch (nome) {
                case "buscarNaBaseDeConhecimento" -> {
                    // Extrai o parâmetro "consulta" do JSON de argumentos
                    // Exemplo simplificado — use ObjectMapper em produção
                    String consulta = extrairParametro(argumentosJson, "consulta");
                    yield agentTools.buscarNaBaseDeConhecimento(consulta);
                }
                case "obterDataHoraAtual" -> agentTools.obterDataHoraAtual();
                default -> "Ferramenta desconhecida: " + nome;
            };
        }

        /** Extração simplificada de parâmetro do JSON. Use ObjectMapper em produção. */
        private String extrairParametro(String json, String chave) {
            // Exemplo: {"consulta": "capital do brasil"} → "capital do brasil"
            // Em produção: objectMapper.readTree(json).get(chave).asText()
            int inicio = json.indexOf("\"" + chave + "\"");
            if (inicio == -1) return json;
            int valorInicio = json.indexOf("\"", inicio + chave.length() + 3) + 1;
            int valorFim = json.indexOf("\"", valorInicio);
            return json.substring(valorInicio, valorFim);
        }
    }


    // ================================================================
    // BLOCO 5 — CONTROLLER (ENDPOINTS REST)
    // Crie como: src/main/java/TF_ESII/TF/agent/AgentController.java
    //
    // Define os pontos de entrada HTTP do Agent Service.
    // O serviço se registra no Eureka (naming-server:8765) e pode
    // ser descoberto pelos outros microserviços pelo nome "agent-service".
    // ================================================================

    @RestController
    @RequestMapping("/api/agent")
    static class AgentController {

        private final AgentService agentService;

        AgentController(AgentService agentService) {
            this.agentService = agentService;
        }

        /**
         * Endpoint principal: recebe a mensagem do usuário e executa o ciclo agêntico.
         *
         * POST /api/agent/chat
         * Body: { "message": "Quais serviços estão registrados?", "sessionId": "abc123" }
         *
         * Resposta:
         * {
         *   "sessionId": "abc123",
         *   "answer": "Os serviços registrados são...",
         *   "toolsUsed": ["buscarNaBaseDeConhecimento"],
         *   "iterations": 2
         * }
         *
         * Se sessionId for omitido, uma nova sessão é criada automaticamente.
         * Para manter contexto de conversa multi-turno, reutilize o sessionId
         * retornado na resposta.
         */
        @PostMapping("/chat")
        public ResponseEntity<AgentResponse> chat(@RequestBody AgentRequest request) {
            AgentResponse resposta = agentService.processar(request);
            return ResponseEntity.ok(resposta);
        }

        /**
         * Endpoint de nova sessão: para iniciar uma conversa limpa.
         *
         * POST /api/agent/nova-sessao
         * Body: { "message": "Olá, quero saber sobre..." }
         */
        @PostMapping("/nova-sessao")
        public ResponseEntity<AgentResponse> novaSessao(@RequestBody Map<String, String> body) {
            var request = new AgentRequest(body.get("message"), null); // null → nova sessão
            return ResponseEntity.ok(agentService.processar(request));
        }

        /**
         * Health check do serviço.
         *
         * GET /api/agent/health
         */
        @GetMapping("/health")
        public ResponseEntity<Map<String, String>> health() {
            return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "agent-service",
                "timestamp", LocalDateTime.now().toString()
            ));
        }
    }


    // ================================================================
    // BLOCO 6 — ADIÇÕES AO application.yaml
    //
    // Adicione as seções abaixo ao application.yaml do agent-service.
    // Não mexa no application.yaml do naming-server.
    //
    // ================================================================
    //
    //  spring:
    //    application:
    //      name: agent-service          ← nome que aparece no Eureka
    //
    //    ai:
    //      ollama:
    //        base-url: http://localhost:11434
    //        chat:
    //          options:
    //            model: llama3.2        ← modelo instalado no Ollama
    //            temperature: 0.3       ← menor = mais determinístico
    //            num-ctx: 8192          ← tamanho do contexto
    //
    //      vectorstore:
    //        qdrant:
    //          host: localhost
    //          port: 6334
    //          collection-name: agent-knowledge
    //          use-tls: false
    //
    //  eureka:
    //    client:
    //      service-url:
    //        defaultZone: http://localhost:8765/eureka  ← aponta para naming-server
    //    instance:
    //      prefer-ip-address: true
    //
    //  server:
    //    port: 8090                     ← porta do agent-service
    //
    // ================================================================
    //
    //  DEPENDÊNCIAS ADICIONAIS para o pom.xml do agent-service:
    //
    //  <dependency>
    //    <groupId>org.springframework.boot</groupId>
    //    <artifactId>spring-boot-starter-web</artifactId>
    //  </dependency>
    //
    //  <dependency>
    //    <groupId>org.springframework.cloud</groupId>
    //    <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
    //  </dependency>
    //
    //  E adicione @EnableDiscoveryClient na classe principal:
    //
    //  @SpringBootApplication
    //  @EnableDiscoveryClient
    //  public class AgentServiceApplication { ... }
    //
    // ================================================================
}
