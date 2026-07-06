package TF_ESII.TF.agent;

import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import TF_ESII.TF.DTO.AgentRequest;
import TF_ESII.TF.DTO.AgentResponse;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final AgentService agentService;
    private final AgentTools agentTools;

    AgentController(AgentService agentService, AgentTools agentTools) {
        this.agentService = agentService;
        this.agentTools = agentTools;
    }

    @PostMapping("/chat")
    public ResponseEntity<AgentResponse> chat(@RequestBody AgentRequest agentRequest) {
        AgentResponse resposta = agentService.processar(agentRequest);
        return ResponseEntity.ok(resposta);
    }

    @PostMapping("/nova-sessao")
    public ResponseEntity<AgentResponse> novaSessao(@RequestBody Map<String, String> body) {
        var request = new AgentRequest(body.get("message"), null);
        return ResponseEntity.ok(agentService.processar(request));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "agent-service",
            "timestamp", LocalDateTime.now().toString()
        ));
    }

    // Chama a ferramenta de retrieval diretamente, sem passar pelo ciclo ReAct/LLM.
    // Serve para validar a integração agent-service <-> retrieval-service isoladamente.
    @GetMapping("/debug/retrieval")
    public ResponseEntity<Map<String, String>> testarRetrieval(@RequestParam String query) {
        String resultado = agentTools.buscarNaBaseDeConhecimento(query);
        return ResponseEntity.ok(Map.of(
            "query", query,
            "resultado", resultado
        ));
    }
}
