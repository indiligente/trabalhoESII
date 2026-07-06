package TF_ESII.TF.telemetry;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import TF_ESII.TF.config.TelemetryRabbitConfig;

@Component
public class TelemetryConsumer {

    private static final Logger logger = LoggerFactory.getLogger(TelemetryConsumer.class);

    @RabbitListener(queues = TelemetryRabbitConfig.AGENT_TELEMETRY_QUEUE)
    public void consume(Map<String, Object> telemetry) {
        logger.info(
            "Telemetry event received: sessionId={}, iteration={}, durationMs={}, finishReason={}, timestamp={}",
            telemetry.get("sessionId"),
            telemetry.get("iteracao"),
            telemetry.get("duracaoMs"),
            telemetry.get("finishReason"),
            telemetry.get("timestamp")
        );
    }
}
