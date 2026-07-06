package TF_ESII.TF.agent;

import java.time.LocalDateTime;
import org.springframework.stereotype.Component;
import TF_ESII.TF.DTO.retrieval.RetrievalSearchRequest;
import TF_ESII.TF.DTO.retrieval.RetrievalSearchResponse;
import TF_ESII.TF.feign.RetrievalServiceClient;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;

@Component
public class AgentTools {

    private final RetrievalServiceClient retrievalServiceClient;

    public AgentTools(RetrievalServiceClient retrievalServiceClient) {
        this.retrievalServiceClient = retrievalServiceClient;
    }

    // Ferramenta 1: busca RAG no retrieval-service via OpenFeign
    @CircuitBreaker(name = "retrievalService", fallbackMethod = "fallbackBuscarNaBaseDeConhecimento")
    public String buscarNaBaseDeConhecimento(String consulta) {
        RetrievalSearchResponse resposta = retrievalServiceClient.search(new RetrievalSearchRequest(consulta, 4));
        if (resposta == null || resposta.results() == null || resposta.results().isEmpty()) {
            return "Nenhum documento relevante encontrado na base de conhecimento.";
        }
        return resposta.results().stream()
            .map(r -> "- " + r.content() + " (score=" + r.score() + ")")
            .reduce((a, b) -> a + "\n" + b)
            .orElse("Nenhum documento relevante encontrado na base de conhecimento.");
    }

    public String fallbackBuscarNaBaseDeConhecimento(String consulta, Throwable error) {
        return "Base de conhecimento indisponivel no momento. Consulta nao executada: " + consulta;
    }

    // Ferramenta 2: data/hora atual (sem dependência externa)
    public String obterDataHoraAtual() {
        return LocalDateTime.now().toString();
    }
}
