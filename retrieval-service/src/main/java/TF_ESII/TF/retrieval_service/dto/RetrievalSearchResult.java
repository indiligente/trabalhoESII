package TF_ESII.TF.retrieval_service.dto;

import java.util.Map;

public record RetrievalSearchResult(String id, String content, Double score, Map<String, Object> metadata) {}
