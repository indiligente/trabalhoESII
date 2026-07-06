package TF_ESII.tool_registry.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseQueryToolTest {

    private DatabaseQueryTool dbTool;

    @BeforeEach
    void setUp() {
        dbTool = new DatabaseQueryTool();
    }

    @Test
    void consultaUsuarioExistente() {
        Map<String, Object> result = dbTool.execute(Map.of("key", "user:1"));
        assertThat(result.get("found")).isEqualTo(true);
        assertThat(result).containsKey("record");
    }

    @Test
    void consultaProdutoExistente() {
        Map<String, Object> result = dbTool.execute(Map.of("key", "product:1"));
        assertThat(result.get("found")).isEqualTo(true);
    }

    @Test
    void chaveInexistenteRetornaFoundFalse() {
        Map<String, Object> result = dbTool.execute(Map.of("key", "user:999"));
        assertThat(result.get("found")).isEqualTo(false);
    }

    @Test
    void parametroAusenteRetornaErro() {
        Map<String, Object> result = dbTool.execute(Map.of());
        assertThat(result).containsKey("error");
    }
}
