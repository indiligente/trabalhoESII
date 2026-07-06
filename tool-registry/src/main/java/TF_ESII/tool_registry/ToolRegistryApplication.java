package TF_ESII.tool_registry;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class ToolRegistryApplication {
    public static void main(String[] args) {
        SpringApplication.run(ToolRegistryApplication.class, args);
    }
}
