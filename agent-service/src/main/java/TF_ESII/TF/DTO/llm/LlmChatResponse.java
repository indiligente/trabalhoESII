package TF_ESII.TF.DTO.llm;

import java.util.List;

public record LlmChatResponse(String content, List<ToolCall> toolCalls, String finishReason) {}
