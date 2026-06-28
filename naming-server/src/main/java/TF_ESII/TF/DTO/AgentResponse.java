package TF_ESII.TF.DTO;

import java.util.List;

public record AgentResponse(
    String sessionId,
    String answer,
    List<String> toolsUsed,
    int iterations
) {}
