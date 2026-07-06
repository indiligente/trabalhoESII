package TF_ESII.TF.retrieval_service;


import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;

@Service
public class Retrieval {

    @Autowired
    private VectorStore vectorStore;

	public void salvarDocumento(String conteudo, Map<String, Object> metadados){
		Document documento = new Document(conteudo, metadados);

		vectorStore.add(List.of(documento));
	}

	public List<Document> buscarDocumentos(String perguntaUsuario, int topK){
		SearchRequest requisicao = SearchRequest.builder()
			.query(perguntaUsuario)
			.topK(topK)
			.similarityThreshold(0.3)
			.build();
		return vectorStore.similaritySearch(requisicao);
	}
}