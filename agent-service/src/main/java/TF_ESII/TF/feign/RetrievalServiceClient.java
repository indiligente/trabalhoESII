package TF_ESII.TF.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import TF_ESII.TF.DTO.retrieval.RetrievalSearchRequest;
import TF_ESII.TF.DTO.retrieval.RetrievalSearchResponse;

@FeignClient(name = "retrieval-service", url = "${RETRIEVAL_SERVICE_URL:http://localhost:8766}")
public interface RetrievalServiceClient {

    @PostMapping("/api/retrieval/search")
    RetrievalSearchResponse search(@RequestBody RetrievalSearchRequest request);
}
