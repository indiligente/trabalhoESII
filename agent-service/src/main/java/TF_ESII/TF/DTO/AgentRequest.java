package TF_ESII.TF.DTO;

import java.util.UUID;

public record AgentRequest(
    String message,
    String sessionId
) {
    public AgentRequest {
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString();
        }
    }
}
