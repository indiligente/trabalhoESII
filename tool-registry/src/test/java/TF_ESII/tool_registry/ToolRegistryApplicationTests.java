package TF_ESII.tool_registry;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ToolRegistryApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void contextLoads() {
    }

    @Test
    void builtInsCarregadosNaInicializacao() throws Exception {
        mockMvc.perform(get("/tools"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(4))
            .andExpect(jsonPath("$[?(@.name == 'calculator')]").exists())
            .andExpect(jsonPath("$[?(@.name == 'echo')]").exists())
            .andExpect(jsonPath("$[?(@.name == 'database-query')]").exists())
            .andExpect(jsonPath("$[?(@.name == 'datetime')]").exists());
    }
}
