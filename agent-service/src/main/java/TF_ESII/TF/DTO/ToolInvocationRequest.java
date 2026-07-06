package TF_ESII.TF.DTO;

import java.util.Map;

public class ToolInvocationRequest {
    private Map<String, Object> params;

    public ToolInvocationRequest(Map<String, Object> params) {
        this.params = params;
    }

    public Map<String, Object> getParams() {
        return params;
    }
}
