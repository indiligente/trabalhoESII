package TF_ESII.tool_registry.repository;

import TF_ESII.tool_registry.model.Tool;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ToolRepository extends JpaRepository<Tool, Long> {
    Optional<Tool> findByName(String name);
    boolean existsByName(String name);
}
