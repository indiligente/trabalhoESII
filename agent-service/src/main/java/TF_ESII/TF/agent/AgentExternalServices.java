package TF_ESII.TF.agent;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import TF_ESII.TF.DTO.Tool;
import TF_ESII.TF.DTO.ToolInvocationRequest;
import TF_ESII.TF.DTO.llm.LlmChatRequest;
import TF_ESII.TF.DTO.llm.LlmChatResponse;
import TF_ESII.TF.DTO.llm.LlmMessage;
import TF_ESII.TF.feign.LlmGatewayClient;
import TF_ESII.TF.feign.MemoryServiceClient;
import TF_ESII.TF.feign.ToolRegistryClient;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;

@Component
public class AgentExternalServices {

    private static final Logger log = LoggerFactory.getLogger(AgentExternalServices.class);

    private final LlmGatewayClient llmGatewayClient;
    private final MemoryServiceClient memoryServiceClient;
    private final ToolRegistryClient toolRegistryClient;

    public AgentExternalServices(
        LlmGatewayClient llmGatewayClient,
        MemoryServiceClient memoryServiceClient,
        ToolRegistryClient toolRegistryClient
    ) {
        this.llmGatewayClient = llmGatewayClient;
        this.memoryServiceClient = memoryServiceClient;
        this.toolRegistryClient = toolRegistryClient;
    }

    @CircuitBreaker(name = "llmGateway", fallbackMethod = "fallbackChat")
    public LlmChatResponse chat(LlmChatRequest request) {
        return llmGatewayClient.chat(request);
    }

    @CircuitBreaker(name = "memoryService", fallbackMethod = "fallbackLoadHistory")
    public List<LlmMessage> loadHistory(String sessionId) {
        return memoryServiceClient.loadHistory(sessionId);
    }

    @CircuitBreaker(name = "memoryService", fallbackMethod = "fallbackSaveMessage")
    public void saveMessage(String sessionId, LlmMessage message) {
        memoryServiceClient.saveMessage(sessionId, message);
    }

    @CircuitBreaker(name = "toolRegistry", fallbackMethod = "fallbackListTools")
    public List<Tool> listTools() {
        return toolRegistryClient.listTools();
    }

    @CircuitBreaker(name = "toolRegistry", fallbackMethod = "fallbackExecuteTool")
    public String executeTool(String name, ToolInvocationRequest request) {
        return toolRegistryClient.executeTool(name, request);
    }

    public LlmChatResponse fallbackChat(LlmChatRequest request, Throwable error) {
        log.warn("Circuit breaker fallback acionado para llm-gateway: {}", error.getMessage());
        return new LlmChatResponse(
            "O LLM Gateway esta temporariamente indisponivel. Tente novamente em alguns instantes.",
            List.of(),
            "circuit_breaker_fallback"
        );
    }

    public List<LlmMessage> fallbackLoadHistory(String sessionId, Throwable error) {
        log.warn("Circuit breaker fallback acionado ao carregar memoria da sessao {}: {}", sessionId, error.getMessage());
        return new ArrayList<>();
    }

    public void fallbackSaveMessage(String sessionId, LlmMessage message, Throwable error) {
        log.warn("Circuit breaker fallback acionado ao salvar memoria da sessao {}: {}", sessionId, error.getMessage());
    }

    public List<Tool> fallbackListTools(Throwable error) {
        log.warn("Circuit breaker fallback acionado ao listar ferramentas remotas: {}", error.getMessage());
        return new ArrayList<>();
    }

    public String fallbackExecuteTool(String name, ToolInvocationRequest request, Throwable error) {
        log.warn("Circuit breaker fallback acionado ao executar ferramenta remota {}: {}", name, error.getMessage());
        return "Ferramenta remota '" + name + "' indisponivel no momento.";
    }
}
