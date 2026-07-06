package TF_ESII.TF.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TelemetryRabbitConfig {

    public static final String AGENT_TELEMETRY_QUEUE = "agent.telemetry";

    @Bean
    Queue agentTelemetryQueue() {
        return QueueBuilder.durable(AGENT_TELEMETRY_QUEUE).build();
    }

    @Bean
    MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
