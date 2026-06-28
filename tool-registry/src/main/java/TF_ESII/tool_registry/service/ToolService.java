package TF_ESII.tool_registry.service;

import TF_ESII.tool_registry.model.Tool;
import TF_ESII.tool_registry.repository.ToolRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ToolService {

    private final ToolRepository repository;
    private final ToolExecutorService executor;

    public ToolService(ToolRepository repository, ToolExecutorService executor) {
        this.repository = repository;
        this.executor = executor;
    }

    public List<Tool> listAll() {
        return repository.findAll();
    }

    public Optional<Tool> findByName(String name) {
        return repository.findByName(name);
    }

    public Tool register(Tool tool) {
        if (repository.existsByName(tool.getName())) {
            throw new IllegalArgumentException("Tool with name '" + tool.getName() + "' already exists.");
        }
        return repository.save(tool);
    }

    public Tool update(String name, Tool updated) {
        Tool existing = repository.findByName(name)
            .orElseThrow(() -> new IllegalArgumentException("Tool not found: " + name));
        existing.setDescription(updated.getDescription());
        existing.setParametersSchema(updated.getParametersSchema());
        return repository.save(existing);
    }

    public void delete(String name) {
        Tool tool = repository.findByName(name)
            .orElseThrow(() -> new IllegalArgumentException("Tool not found: " + name));
        if (tool.isBuiltIn()) {
            throw new IllegalStateException("Cannot delete built-in tool: " + name);
        }
        repository.delete(tool);
    }

    public Object execute(String name, Map<String, Object> params) {
        repository.findByName(name)
            .orElseThrow(() -> new IllegalArgumentException("Tool not found: " + name));
        return executor.execute(name, params);
    }
}
