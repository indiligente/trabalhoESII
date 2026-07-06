package TF_ESII.tool_registry.controller;

import TF_ESII.tool_registry.model.Tool;
import TF_ESII.tool_registry.model.ToolInvocationRequest;
import TF_ESII.tool_registry.service.ToolService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/tools")
public class ToolController {

    private final ToolService service;

    public ToolController(ToolService service) {
        this.service = service;
    }

    @GetMapping
    public List<Tool> listTools() {
        return service.listAll();
    }

    @GetMapping("/{name}")
    public ResponseEntity<Tool> getTool(@PathVariable String name) {
        return service.findByName(name)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> registerTool(@RequestBody Tool tool) {
        try {
            Tool saved = service.register(tool);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{name}")
    public ResponseEntity<?> updateTool(@PathVariable String name, @RequestBody Tool tool) {
        try {
            Tool updated = service.update(name, tool);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{name}")
    public ResponseEntity<?> deleteTool(@PathVariable String name) {
        try {
            service.delete(name);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{name}/execute")
    public ResponseEntity<?> executeTool(@PathVariable String name,
                                         @RequestBody(required = false) ToolInvocationRequest request) {
        try {
            Map<String, Object> params = request != null ? request.getParams() : Map.of();
            Object result = service.execute(name, params);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
