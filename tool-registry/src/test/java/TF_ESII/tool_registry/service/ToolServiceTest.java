package TF_ESII.tool_registry.service;

import TF_ESII.tool_registry.model.Tool;
import TF_ESII.tool_registry.repository.ToolRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ToolServiceTest {

    @Mock
    private ToolRepository repository;

    @Mock
    private ToolExecutorService executor;

    @InjectMocks
    private ToolService service;

    private Tool tool;

    @BeforeEach
    void setUp() {
        tool = new Tool("calculator", "Calculadora", "{}", true);
    }

    @Test
    void listAllRetornaTodasFerramentas() {
        when(repository.findAll()).thenReturn(List.of(tool));

        List<Tool> result = service.listAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("calculator");
    }

    @Test
    void findByNameRetornaFerramenta() {
        when(repository.findByName("calculator")).thenReturn(Optional.of(tool));

        Optional<Tool> result = service.findByName("calculator");

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("calculator");
    }

    @Test
    void registerSalvaNovaFerramenta() {
        Tool nova = new Tool("echo", "Echo", "{}", false);
        when(repository.existsByName("echo")).thenReturn(false);
        when(repository.save(nova)).thenReturn(nova);

        Tool result = service.register(nova);

        assertThat(result.getName()).isEqualTo("echo");
        verify(repository).save(nova);
    }

    @Test
    void registerComNomeDuplicadoLancaExcecao() {
        when(repository.existsByName("calculator")).thenReturn(true);

        assertThatThrownBy(() -> service.register(tool))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already exists");
    }

    @Test
    void deleteFerramentaCustomFunciona() {
        Tool custom = new Tool("minha-tool", "Custom", "{}", false);
        when(repository.findByName("minha-tool")).thenReturn(Optional.of(custom));

        service.delete("minha-tool");

        verify(repository).delete(custom);
    }

    @Test
    void deleteFerramentaBuiltInLancaExcecao() {
        when(repository.findByName("calculator")).thenReturn(Optional.of(tool));

        assertThatThrownBy(() -> service.delete("calculator"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("built-in");
    }

    @Test
    void deleteFerramentaInexistenteLancaExcecao() {
        when(repository.findByName("x")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete("x"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not found");
    }

    @Test
    void executeDespachaParaExecutor() {
        when(repository.findByName("calculator")).thenReturn(Optional.of(tool));
        when(executor.execute("calculator", Map.of())).thenReturn(Map.of("result", 4.0));

        Object result = service.execute("calculator", Map.of());

        assertThat((Map<String, Object>) result).containsKey("result");
        verify(executor).execute("calculator", Map.of());
    }

    @Test
    void executeFerramentaInexistenteLancaExcecao() {
        when(repository.findByName("x")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.execute("x", Map.of()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateAtualizaFerramenta() {
        Tool atualizada = new Tool("calculator", "Nova descrição", "{novo}", true);
        when(repository.findByName("calculator")).thenReturn(Optional.of(tool));
        when(repository.save(tool)).thenReturn(tool);

        service.update("calculator", atualizada);

        assertThat(tool.getDescription()).isEqualTo("Nova descrição");
        assertThat(tool.getParametersSchema()).isEqualTo("{novo}");
        verify(repository).save(tool);
    }

    @Test
    void updateFerramentaInexistenteLancaExcecao() {
        when(repository.findByName("x")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update("x", tool))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not found");
    }
}
