package TF_ESII.TF.DTO.llm;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

// availableTools é omitido do JSON quando null — o llm-gateway espera null ou List[dict],
// não List[str], e nem usa o campo na chamada ao litellm por enquanto.
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LlmChatRequest(List<LlmMessage> messages, List<String> availableTools) {}
