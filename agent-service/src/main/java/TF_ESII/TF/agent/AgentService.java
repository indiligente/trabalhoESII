package TF_ESII.TF.agent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import TF_ESII.TF.DTO.AgentRequest;
import TF_ESII.TF.DTO.AgentResponse;
import TF_ESII.TF.DTO.llm.LlmChatRequest;
import TF_ESII.TF.DTO.llm.LlmChatResponse;
import TF_ESII.TF.DTO.llm.LlmMessage;
import TF_ESII.TF.feign.LlmGatewayClient;
import TF_ESII.TF.feign.MemoryServiceClient;

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

    private final LlmGatewayClient llmGatewayClient;
    private final MemoryServiceClient memoryServiceClient;
    private final AgentTools agentTools;
    private final RabbitTemplate rabbitTemplate;

    public AgentService(LlmGatewayClient llmGatewayClient, MemoryServiceClient memoryServiceClient, AgentTools agentTools, RabbitTemplate rabbitTemplate) {
        this.llmGatewayClient = llmGatewayClient;
        this.memoryServiceClient = memoryServiceClient;
        this.agentTools = agentTools;
        this.rabbitTemplate = rabbitTemplate;
    }

    public AgentResponse processar(AgentRequest request) {
        // Monta histórico: system prompt + histórico persistido no memory-service
        List<LlmMessage> historico = new ArrayList<>();
        historico.add(new LlmMessage("system", PROMPT_SISTEMA));

        try {
            List<LlmMessage> historicoSalvo = memoryServiceClient.loadHistory(request.sessionId());
            if (historicoSalvo != null) historico.addAll(historicoSalvo);
        } catch (Exception e) {
            // memory-service indisponível — continua com histórico local vazio
        }

        LlmMessage mensagemUsuario = new LlmMessage("user", request.message());
        historico.add(mensagemUsuario);
        salvarMensagem(request.sessionId(), mensagemUsuario);

        List<String> ferramentasUsadas = new ArrayList<>();
        int iteracao = 0;

        while (iteracao < MAX_ITERACOES) {
            iteracao++;
            long inicio = Instant.now().toEpochMilli();

            LlmChatResponse respostaLLM = llmGatewayClient.chat(
                new LlmChatRequest(historico, null)
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

                String resultado = executarFerramenta(toolCall.name(), toolCall.arguments());

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
        try {
            memoryServiceClient.saveMessage(sessionId, mensagem);
        } catch (Exception e) {
            // memory-service indisponível — sem persistência neste turno
        }
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
}
