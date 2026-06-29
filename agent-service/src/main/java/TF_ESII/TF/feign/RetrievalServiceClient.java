package TF_ESII.TF.feign;

import java.util.Map;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "retrieval-service")
public interface RetrievalServiceClient {

    @PostMapping("/api/retrieval/search")
    String search(@RequestBody Map<String, Object> request);
}
