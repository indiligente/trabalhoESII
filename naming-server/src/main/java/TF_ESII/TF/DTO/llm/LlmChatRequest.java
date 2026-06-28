package TF_ESII.TF.DTO.llm;

import java.util.List;

public record LlmChatRequest(List<LlmMessage> messages, List<String> availableTools) {}
