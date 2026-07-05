package TF_ESII.TF.retrieval_service.dto;

public record RetrievalSearchRequest(String query, Integer topK) {
    public RetrievalSearchRequest {
        if (topK == null || topK <= 0) {
            topK = 4;
        }
    }
}
