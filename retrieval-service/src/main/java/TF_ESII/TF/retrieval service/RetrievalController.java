package TF_ESII.TF.retrieval_service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

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
	public List<Documento> pesquisar(@RequestParam String pergunta){
		return retrieval.buscarDocumentos(pergunta);
	}
    
}