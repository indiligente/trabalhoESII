package TF_ESII.TF.retrieval_service;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;
import javax.naming.directory.SearchResult;

@Service
public class Retrieval {

    @Autowired
    private VectorStore vectorStore;

	public void salvarDocumento(String conteudo, Map<Strinf, Object> metadados){
		Document documento = new Documento(conteudo, metadados);

		vectorStore.add(Lis.of(documento));
	}

	public List<Document> buscarDocumentos(String perguntaUsuario){
		SearchRequest requisicao = SearchResult.builder()
			.query(perguntaUsuario)
			.topK(3)
			.similaityThreshold(0.7)
			.build;
		return vectorStore.similaritySearch(requisicao);
	}
}