package TF_ESII.TF.agent;

import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import TF_ESII.TF.DTO.AgentRequest;
import TF_ESII.TF.DTO.AgentResponse;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final AgentService agentService;

    AgentController(AgentService agentService) {
        this.agentService = agentService;
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
}
