package TF_ESII.TF.retrieval_service;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.ai.document.Document;
import java.util.Map;




@RestController
@RequestMapping("/retrieval")
public class RetrievalController {
	@Autowired
	private Retrieval retrieval;

	@PostMapping("/getting")
	public String getDocumento(@RequestBody String conteudo){
		retrieval.salvarDocumento(conteudo, Map.of("origem","manual"));
		return "Documento indexado.";
	}

	@GetMapping("/search")
	public List<Document> pesquisar(@RequestParam String pergunta){
		return retrieval.buscarDocumentos(pergunta);
	}
    
}