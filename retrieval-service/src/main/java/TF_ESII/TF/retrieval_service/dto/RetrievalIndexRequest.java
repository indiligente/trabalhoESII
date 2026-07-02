package TF_ESII.TF.retrieval_service.dto;

import java.util.Map;

public record RetrievalIndexRequest(String content, Map<String, Object> metadata) {}
