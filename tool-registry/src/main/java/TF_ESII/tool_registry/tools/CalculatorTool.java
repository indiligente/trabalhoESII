package TF_ESII.tool_registry.tools;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class CalculatorTool {

    // Matches: optional-negative-number  operator  optional-negative-number
    private static final Pattern BINARY_OP = Pattern.compile(
        "^\\s*(-?[\\d.]+)\\s*([+\\-*/])\\s*(-?[\\d.]+)\\s*$"
    );

    public Map<String, Object> execute(Map<String, Object> params) {
        String expression = (String) params.get("expression");
        if (expression == null) {
            return Map.of("error", "Missing required parameter: expression");
        }
        try {
            double result = eval(expression.trim());
            return Map.of("expression", expression, "result", result);
        } catch (Exception e) {
            return Map.of("error", "Could not evaluate expression: " + e.getMessage());
        }
    }

    private double eval(String expr) {
        Matcher m = BINARY_OP.matcher(expr);
        if (m.matches()) {
            double left  = Double.parseDouble(m.group(1));
            String op    = m.group(2);
            double right = Double.parseDouble(m.group(3));
            return switch (op) {
                case "+" -> left + right;
                case "-" -> left - right;
                case "*" -> left * right;
                case "/" -> {
                    if (right == 0) throw new ArithmeticException("Division by zero");
                    yield left / right;
                }
                default -> throw new IllegalArgumentException("Unknown operator: " + op);
            };
        }
        return Double.parseDouble(expr);
    }
}
