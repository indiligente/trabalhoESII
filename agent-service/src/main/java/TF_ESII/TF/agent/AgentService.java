package TF_ESII.TF.agent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import TF_ESII.TF.DTO.AgentRequest;
import TF_ESII.TF.DTO.AgentResponse;
import TF_ESII.TF.DTO.llm.LlmChatRequest;
import TF_ESII.TF.DTO.llm.LlmChatResponse;
import TF_ESII.TF.DTO.llm.LlmMessage;
import TF_ESII.TF.DTO.Tool;
import TF_ESII.TF.DTO.ToolInvocationRequest;

@Service
public class AgentService {

    private static final int MAX_ITERACOES = 10;
    private static final String FILA_TELEMETRIA = "agent.telemetry";

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

    private final AgentTools agentTools;
    private final RabbitTemplate rabbitTemplate;
    private final AgentExternalServices externalServices;

    public AgentService(AgentTools agentTools, RabbitTemplate rabbitTemplate, AgentExternalServices externalServices) {
        this.agentTools = agentTools;
        this.rabbitTemplate = rabbitTemplate;
        this.externalServices = externalServices;
    }

    public AgentResponse processar(AgentRequest request) {
        // Monta histórico: system prompt + histórico persistido no memory-service
        List<LlmMessage> historico = new ArrayList<>();
        historico.add(new LlmMessage("system", PROMPT_SISTEMA));

        List<LlmMessage> historicoSalvo = externalServices.loadHistory(request.sessionId());
        if (historicoSalvo != null) historico.addAll(historicoSalvo);

        LlmMessage mensagemUsuario = new LlmMessage("user", request.message());
        historico.add(mensagemUsuario);
        salvarMensagem(request.sessionId(), mensagemUsuario);

        List<String> ferramentasUsadas = new ArrayList<>();
        int iteracao = 0;
        
        List<Tool> ferramentasDisponiveis = externalServices.listTools();
        List<Map<String, Object>> toolsForLlm = formatToolsForLlm(ferramentasDisponiveis);
        
        while (iteracao < MAX_ITERACOES) {
            iteracao++;
            long inicio = Instant.now().toEpochMilli();

            LlmChatResponse respostaLLM = externalServices.chat(
                new LlmChatRequest(historico, toolsForLlm)
            );

            long duracaoMs = Instant.now().toEpochMilli() - inicio;
            publicarTelemetria(request.sessionId(), iteracao, duracaoMs, respostaLLM.finishReason());

            if (respostaLLM.toolCalls() == null || respostaLLM.toolCalls().isEmpty()) {
                LlmMessage resposta = new LlmMessage("assistant", respostaLLM.content());
                historico.add(resposta);
                salvarMensagem(request.sessionId(), resposta);

                return new AgentResponse(
                    request.sessionId(),
                    respostaLLM.content(),
                    ferramentasUsadas,
                    iteracao
                );
            }

            String toolCallsDesc = respostaLLM.toolCalls().stream()
                .map(tc -> tc.name() + "(" + tc.arguments() + ")")
                .reduce((a, b) -> a + ", " + b).orElse("");
            historico.add(new LlmMessage("assistant", "[tool_calls: " + toolCallsDesc + "]"));

            for (var toolCall : respostaLLM.toolCalls()) {
                ferramentasUsadas.add(toolCall.name());

                String resultado;
                if ("buscarNaBaseDeConhecimento".equals(toolCall.name())) {
                    String consulta = (String) ((Map<String, Object>) toolCall.arguments()).get("consulta");
                    resultado = agentTools.buscarNaBaseDeConhecimento(consulta);
                } else if ("obterDataHoraAtual".equals(toolCall.name())) {
                    resultado = agentTools.obterDataHoraAtual();
                } else {
                    ToolInvocationRequest requestPayload = new ToolInvocationRequest((Map<String, Object>) toolCall.arguments());
                    resultado = externalServices.executeTool(toolCall.name(), requestPayload);
                }

                LlmMessage observacao = new LlmMessage("tool", "id=" + toolCall.id() + " name=" + toolCall.name() + " result=" + resultado);
                historico.add(observacao);
                salvarMensagem(request.sessionId(), observacao);
            }
        }

        return new AgentResponse(
            request.sessionId(),
            "Não foi possível completar a tarefa no número máximo de iterações. Tente reformular sua pergunta.",
            ferramentasUsadas,
            iteracao
        );
    }

    private String executarFerramenta(String nome, String argumentosJson) {
        return switch (nome) {
            case "buscarNaBaseDeConhecimento" -> {
                String consulta = extrairParametro(argumentosJson, "consulta");
                yield agentTools.buscarNaBaseDeConhecimento(consulta);
            }
            case "obterDataHoraAtual" -> agentTools.obterDataHoraAtual();
            default -> "Ferramenta desconhecida: " + nome;
        };
    }

    private void salvarMensagem(String sessionId, LlmMessage mensagem) {
        externalServices.saveMessage(sessionId, mensagem);
    }

    // Publica métricas de cada chamada LLM na fila RabbitMQ de forma assíncrona
    private void publicarTelemetria(String sessionId, int iteracao, long duracaoMs, String finishReason) {
        try {
            rabbitTemplate.convertAndSend(FILA_TELEMETRIA, Map.of(
                "sessionId", sessionId,
                "iteracao", iteracao,
                "duracaoMs", duracaoMs,
                "finishReason", finishReason != null ? finishReason : "unknown",
                "timestamp", Instant.now().toString()
            ));
        } catch (Exception e) {
            // RabbitMQ indisponível — telemetria descartada, fluxo não é bloqueado
        }
    }

    private String extrairParametro(String json, String chave) {
        int inicio = json.indexOf("\"" + chave + "\"");
        if (inicio == -1) return json;
        int valorInicio = json.indexOf("\"", inicio + chave.length() + 3) + 1;
        int valorFim = json.indexOf("\"", valorInicio);
        if (valorInicio <= 0 || valorFim < 0) return json;
        return json.substring(valorInicio, valorFim);
    }

    private List<Map<String, Object>> formatToolsForLlm(List<Tool> ferramentas) {
        ObjectMapper mapper = new ObjectMapper();
        List<Map<String, Object>> formattedTools = new ArrayList<>();

        if (ferramentas != null) {
            for (Tool tool : ferramentas) {
                try {
                    Map<String, Object> toolMap = new HashMap<>();
                    toolMap.put("type", "function");

                    Map<String, Object> functionMap = new HashMap<>();
                    functionMap.put("name", tool.name());
                    functionMap.put("description", tool.description());

                    if (tool.parametersSchema() != null && !tool.parametersSchema().isBlank()) {
                        Map<String, Object> params = mapper.readValue(tool.parametersSchema(), Map.class);
                        functionMap.put("parameters", params);
                    } else {
                        functionMap.put("parameters", Map.of("type", "object", "properties", Map.of()));
                    }

                    toolMap.put("function", functionMap);
                    formattedTools.add(toolMap);
                } catch (Exception e) {
                }
            }
        }

        // Adiciona ferramentas locais
        try {
            // buscarNaBaseDeConhecimento
            Map<String, Object> ragTool = new HashMap<>();
            ragTool.put("type", "function");

            Map<String, Object> ragFunc = new HashMap<>();
            ragFunc.put("name", "buscarNaBaseDeConhecimento");
            ragFunc.put("description", "Busca informações relevantes na base de conhecimento (RAG) sobre tópicos específicos.");
            ragFunc.put("parameters", Map.of(
                "type", "object",
                "properties", Map.of(
                    "consulta", Map.of(
                        "type", "string",
                        "description", "A consulta ou pergunta para pesquisar na base de conhecimento."
                    )
                ),
                "required", List.of("consulta")
            ));
            ragTool.put("function", ragFunc);
            formattedTools.add(ragTool);

            // obterDataHoraAtual
            Map<String, Object> timeTool = new HashMap<>();
            timeTool.put("type", "function");

            Map<String, Object> timeFunc = new HashMap<>();
            timeFunc.put("name", "obterDataHoraAtual");
            timeFunc.put("description", "Obtém a data e hora atual do sistema.");
            timeFunc.put("parameters", Map.of(
                "type", "object",
                "properties", Map.of(),
                "required", List.of()
            ));
            timeTool.put("function", timeFunc);
            formattedTools.add(timeTool);
        } catch (Exception e) {
        }

        return formattedTools.isEmpty() ? null : formattedTools;
    }
}
