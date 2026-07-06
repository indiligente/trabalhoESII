package TF_ESII.tool_registry.config;

import TF_ESII.tool_registry.model.Tool;
import TF_ESII.tool_registry.repository.ToolRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BuiltInToolsInitializer implements CommandLineRunner {

    private final ToolRepository repository;

    public BuiltInToolsInitializer(ToolRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(String... args) {
        List<Tool> builtIns = List.of(
            new Tool(
                "calculator",
                "Evaluates a simple arithmetic expression (+, -, *, /).",
                """
                {
                  "type": "object",
                  "properties": {
                    "expression": {
                      "type": "string",
                      "description": "Arithmetic expression, e.g. '3 + 5' or '10 / 2'"
                    }
                  },
                  "required": ["expression"]
                }
                """,
                true
            ),
            new Tool(
                "echo",
                "Returns the given message as-is. Useful for testing.",
                """
                {
                  "type": "object",
                  "properties": {
                    "message": {
                      "type": "string",
                      "description": "The message to echo back"
                    }
                  },
                  "required": ["message"]
                }
                """,
                true
            ),
            new Tool(
                "database-query",
                "Consulta um registro por chave (ex: 'user:1', 'product:1').",
                """
                {
                  "type": "object",
                  "properties": {
                    "key": {
                      "type": "string",
                      "description": "Chave do registro, ex: 'user:1'"
                    }
                  },
                  "required": ["key"]
                }
                """,
                true
            ),
            new Tool(
                "datetime",
                "Returns the current date and time in ISO 8601 format.",
                """
                {
                  "type": "object",
                  "properties": {},
                  "required": []
                }
                """,
                true
            )
        );

        for (Tool tool : builtIns) {
            if (!repository.existsByName(tool.getName())) {
                repository.save(tool);
            }
        }
    }
}
