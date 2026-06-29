package TF_ESII.TF.config;

import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentConfig {
    // Sem beans de IA direta — chamadas ao LLM vão via OpenFeign para o llm-gateway
}
