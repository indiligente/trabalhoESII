package TF_ESII.TF.DTO;

import java.util.UUID;

public class AgentRequest {
    record AgentRequestRecord(String message, String idSessao) {
        public AgentRequest{
            if (idSessao == null || idSessao.isBlank()) {
                sessionId = UUID.randomUUID().toString();
            }
        }
    }
}
