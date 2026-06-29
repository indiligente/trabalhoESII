package TF_ESII.TF.agent;

import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.stereotype.Component;
import TF_ESII.TF.feign.RetrievalServiceClient;

@Component
public class AgentTools {

    private final RetrievalServiceClient retrievalServiceClient;

    public AgentTools(RetrievalServiceClient retrievalServiceClient) {
        this.retrievalServiceClient = retrievalServiceClient;
    }

    // Ferramenta 1: busca RAG no retrieval-service via OpenFeign
    public String buscarNaBaseDeConhecimento(String consulta) {
        return retrievalServiceClient.search(Map.of(
            "query", consulta,
            "topK", 4
        ));
    }

    // Ferramenta 2: data/hora atual (sem dependência externa)
    public String obterDataHoraAtual() {
        return LocalDateTime.now().toString();
    }
}
