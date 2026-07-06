package TF_ESII.tool_registry.tools;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Simulates a simple key-value database query.
 * In a real scenario, this would connect to an actual data source.
 */
@Component
public class DatabaseQueryTool {

    private static final Map<String, Object> MOCK_DB = Map.of(
        "user:1", Map.of("id", 1, "name", "Alice", "email", "alice@example.com"),
        "user:2", Map.of("id", 2, "name", "Bob",   "email", "bob@example.com"),
        "product:1", Map.of("id", 1, "name", "Widget", "price", 9.99)
    );

    public Map<String, Object> execute(Map<String, Object> params) {
        String key = (String) params.get("key");
        if (key == null) {
            return Map.of("error", "Missing required parameter: key");
        }

        Object record = MOCK_DB.get(key);
        if (record == null) {
            return Map.of("found", false, "key", key);
        }
        return Map.of("found", true, "key", key, "record", record);
    }
}
