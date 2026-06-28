package TF_ESII.tool_registry.service;

import TF_ESII.tool_registry.tools.CalculatorTool;
import TF_ESII.tool_registry.tools.DatabaseQueryTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ToolExecutorServiceTest {

    private ToolExecutorService executor;

    @BeforeEach
    void setUp() {
        executor = new ToolExecutorService(new CalculatorTool(), new DatabaseQueryTool());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object o) {
        return (Map<String, Object>) o;
    }

    @Test
    void executaCalculator() {
        Object result = executor.execute("calculator", Map.of("expression", "2 + 2"));
        assertThat(asMap(result)).containsEntry("result", 4.0);
    }

    @Test
    void executaDatabaseQuery() {
        Object result = executor.execute("database-query", Map.of("key", "user:1"));
        Map<String, Object> map = asMap(result);
        assertThat(map).containsEntry("found", true);
        assertThat(map).containsEntry("key", "user:1");
        assertThat(map).containsKey("record");
    }

    @Test
    void executaEcho() {
        Object result = executor.execute("echo", Map.of("message", "olá"));
        assertThat(asMap(result)).containsEntry("echo", "olá");
    }

    @Test
    void executaDatetime() {
        Object result = executor.execute("datetime", Map.of());
        assertThat(asMap(result)).containsKey("datetime");
    }

    @Test
    void ferramentaDesconhecidaRetornaNotImplemented() {
        Object result = executor.execute("ferramenta-inexistente", Map.of());
        assertThat(asMap(result)).containsEntry("status", "not_implemented");
    }
}
