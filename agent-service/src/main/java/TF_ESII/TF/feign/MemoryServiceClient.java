package TF_ESII.TF.feign;

import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import TF_ESII.TF.DTO.llm.LlmMessage;

@FeignClient(name = "memory-service")
public interface MemoryServiceClient {

    @GetMapping("/api/memory/{sessionId}")
    List<LlmMessage> loadHistory(@PathVariable("sessionId") String sessionId);

    @PostMapping("/api/memory/{sessionId}")
    void saveMessage(@PathVariable("sessionId") String sessionId, @RequestBody LlmMessage message);
}
