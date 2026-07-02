package TF_ESII.TF.DTO.retrieval;

import java.util.Map;

public record RetrievalSearchResult(String id, String content, Double score, Map<String, Object> metadata) {}
