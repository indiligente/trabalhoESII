package TF_ESII.TF.DTO.llm;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

// availableTools é omitido do JSON quando null — o llm-gateway espera null ou List[dict]
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LlmChatRequest(List<LlmMessage> messages, List<Map<String, Object>> availableTools) {}
