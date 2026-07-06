package TF_ESII.TF.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import TF_ESII.TF.DTO.llm.LlmChatRequest;
import TF_ESII.TF.DTO.llm.LlmChatResponse;

//@FeignClient(name = "llm-gateway")
@FeignClient(name = "llm-gateway", url = "${LLM_GATEWAY_URL:http://localhost:8767}")
public interface LlmGatewayClient {

    @PostMapping("/api/llm/chat")
    LlmChatResponse chat(@RequestBody LlmChatRequest request);
}
