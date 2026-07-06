package TF_ESII.tool_registry.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CalculatorToolTest {

    private CalculatorTool calculator;

    @BeforeEach
    void setUp() {
        calculator = new CalculatorTool();
    }

    @Test
    void soma() {
        Map<String, Object> result = calculator.execute(Map.of("expression", "3 + 5"));
        assertThat(result.get("result")).isEqualTo(8.0);
    }

    @Test
    void subtracao() {
        Map<String, Object> result = calculator.execute(Map.of("expression", "10 - 4"));
        assertThat(result.get("result")).isEqualTo(6.0);
    }

    @Test
    void multiplicacao() {
        Map<String, Object> result = calculator.execute(Map.of("expression", "6 * 7"));
        assertThat(result.get("result")).isEqualTo(42.0);
    }

    @Test
    void divisao() {
        Map<String, Object> result = calculator.execute(Map.of("expression", "10 / 4"));
        assertThat(result.get("result")).isEqualTo(2.5);
    }

    @Test
    void divisaoPorZeroRetornaErro() {
        Map<String, Object> result = calculator.execute(Map.of("expression", "5 / 0"));
        assertThat(result).containsKey("error");
    }

    @Test
    void parametroAusenteRetornaErro() {
        Map<String, Object> result = calculator.execute(Map.of());
        assertThat(result).containsKey("error");
    }

    @Test
    void operandoNegativo() {
        Map<String, Object> result = calculator.execute(Map.of("expression", "-3 + 5"));
        assertThat(result.get("result")).isEqualTo(2.0);
    }

    @Test
    void subtracacomOperandoNegativo() {
        Map<String, Object> result = calculator.execute(Map.of("expression", "-3 - 5"));
        assertThat(result.get("result")).isEqualTo(-8.0);
    }
}
