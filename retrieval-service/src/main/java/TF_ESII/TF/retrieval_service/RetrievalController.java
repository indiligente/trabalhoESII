package TF_ESII.TF.retrieval_service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import TF_ESII.TF.retrieval_service.dto.RetrievalIndexRequest;
import TF_ESII.TF.retrieval_service.dto.RetrievalSearchRequest;
import TF_ESII.TF.retrieval_service.dto.RetrievalSearchResponse;
import TF_ESII.TF.retrieval_service.dto.RetrievalSearchResult;

@RestController
@RequestMapping("/api/retrieval")
public class RetrievalController {

	@Autowired
	private Retrieval retrieval;

	@PostMapping("/documents")
	public ResponseEntity<Map<String, String>> indexar(@RequestBody RetrievalIndexRequest request) {
		Map<String, Object> metadados = request.metadata() != null ? request.metadata() : Map.of("origem", "manual");
		retrieval.salvarDocumento(request.content(), metadados);
		return ResponseEntity.ok(Map.of("status", "indexed"));
	}

	@PostMapping("/search")
	public ResponseEntity<RetrievalSearchResponse> pesquisar(@RequestBody RetrievalSearchRequest request) {
		List<Document> documentos = retrieval.buscarDocumentos(request.query(), request.topK());
		List<RetrievalSearchResult> resultados = documentos.stream()
			.map(doc -> new RetrievalSearchResult(doc.getId(), doc.getText(), doc.getScore(), doc.getMetadata()))
			.toList();
		return ResponseEntity.ok(new RetrievalSearchResponse(resultados));
	}

	@GetMapping("/health")
	public ResponseEntity<Map<String, String>> health() {
		return ResponseEntity.ok(Map.of(
			"status", "UP",
			"service", "retrieval-service",
			"timestamp", LocalDateTime.now().toString()
		));
	}
}
