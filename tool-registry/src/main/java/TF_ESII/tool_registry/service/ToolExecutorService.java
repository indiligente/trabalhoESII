package TF_ESII.tool_registry.service;

import TF_ESII.tool_registry.tools.CalculatorTool;
import TF_ESII.tool_registry.tools.DatabaseQueryTool;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Map;

@Service
public class ToolExecutorService {

    private final CalculatorTool calculatorTool;
    private final DatabaseQueryTool databaseQueryTool;

    public ToolExecutorService(CalculatorTool calculatorTool, DatabaseQueryTool databaseQueryTool) {
        this.calculatorTool = calculatorTool;
        this.databaseQueryTool = databaseQueryTool;
    }

    public Object execute(String toolName, Map<String, Object> params) {
        return switch (toolName) {
            case "calculator"      -> calculatorTool.execute(params);
            case "database-query"  -> databaseQueryTool.execute(params);
            case "echo"            -> Map.of("echo", params.getOrDefault("message", ""));
            case "datetime"        -> Map.of("datetime", OffsetDateTime.now().toString());
            default -> Map.of(
                "status", "not_implemented",
                "message", "Tool '" + toolName + "' has no built-in executor."
            );
        };
    }
}
