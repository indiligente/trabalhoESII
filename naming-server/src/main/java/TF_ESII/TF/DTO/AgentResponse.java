package TF_ESII.TF.DTO;
import java.util.List;

public class AgentResponse {
    record AgentResponseRecord(String idSessao, String answare, List<String> ferramenta, int iteracoes) {
    }
}
