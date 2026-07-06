package TF_ESII.TF.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import TF_ESII.TF.DTO.Tool;
import TF_ESII.TF.DTO.ToolInvocationRequest;

@FeignClient(name = "tool-registry", url = "${TOOL_REGISTRY_URL:http://tool-registry:8400}")
public interface ToolRegistryClient {
    @GetMapping("/tools")
    List<Tool> listTools();
    
    @PostMapping("/tools/{name}/execute")
    String executeTool(@PathVariable("name") String name, @RequestBody ToolInvocationRequest request);
}
