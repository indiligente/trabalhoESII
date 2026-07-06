package TF_ESII.tool_registry.model;

import java.util.Map;

public class ToolInvocationRequest {

    private Map<String, Object> params;

    public Map<String, Object> getParams() {
        return params != null ? params : Map.of();
    }

    public void setParams(Map<String, Object> params) {
        this.params = params;
    }
}
