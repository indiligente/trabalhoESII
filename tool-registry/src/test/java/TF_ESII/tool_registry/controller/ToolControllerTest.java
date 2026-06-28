package TF_ESII.tool_registry.controller;

import TF_ESII.tool_registry.model.Tool;
import TF_ESII.tool_registry.model.ToolInvocationRequest;
import TF_ESII.tool_registry.service.ToolService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ToolController.class)
class ToolControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ToolService toolService;

    @Test
    void GET_tools_retornaListaVazia() throws Exception {
        when(toolService.listAll()).thenReturn(List.of());

        mockMvc.perform(get("/tools"))
            .andExpect(status().isOk())
            .andExpect(content().json("[]"));
    }

    @Test
    void GET_tools_retornaFerramentas() throws Exception {
        Tool tool = new Tool("calculator", "Calculadora", "{}", true);
        when(toolService.listAll()).thenReturn(List.of(tool));

        mockMvc.perform(get("/tools"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("calculator"));
    }

    @Test
    void GET_toolPorNome_retorna200() throws Exception {
        Tool tool = new Tool("echo", "Echo", "{}", true);
        when(toolService.findByName("echo")).thenReturn(Optional.of(tool));

        mockMvc.perform(get("/tools/echo"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("echo"));
    }

    @Test
    void GET_toolInexistente_retorna404() throws Exception {
        when(toolService.findByName("xyz")).thenReturn(Optional.empty());

        mockMvc.perform(get("/tools/xyz"))
            .andExpect(status().isNotFound());
    }

    @Test
    void POST_tools_registraFerramenta() throws Exception {
        Tool nova = new Tool("minha-tool", "Descrição", "{}", false);
        when(toolService.register(any())).thenReturn(nova);

        mockMvc.perform(post("/tools")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(nova)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("minha-tool"));
    }

    @Test
    void POST_tools_nomeRepetido_retorna409() throws Exception {
        Tool nova = new Tool("calculator", "Desc", "{}", false);
        when(toolService.register(any()))
            .thenThrow(new IllegalArgumentException("Tool already exists."));

        mockMvc.perform(post("/tools")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(nova)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void DELETE_toolCustom_retorna204() throws Exception {
        mockMvc.perform(delete("/tools/minha-tool"))
            .andExpect(status().isNoContent());

        org.mockito.Mockito.verify(toolService).delete("minha-tool");
    }

    @Test
    void DELETE_toolBuiltIn_retorna403() throws Exception {
        org.mockito.Mockito.doThrow(new IllegalStateException("Cannot delete built-in tool"))
            .when(toolService).delete("calculator");

        mockMvc.perform(delete("/tools/calculator"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void POST_execute_retornaResultado() throws Exception {
        when(toolService.execute(eq("calculator"), any()))
            .thenReturn(Map.of("expression", "2 + 2", "result", 4.0));

        String body = """
            { "params": { "expression": "2 + 2" } }
            """;

        mockMvc.perform(post("/tools/calculator/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.result").value(4.0));
    }

    @Test
    void POST_execute_ferramentaInexistente_retorna404() throws Exception {
        when(toolService.execute(eq("xyz"), any()))
            .thenThrow(new IllegalArgumentException("Tool not found"));

        mockMvc.perform(post("/tools/xyz/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void PUT_tool_retorna200() throws Exception {
        Tool atualizada = new Tool("calculator", "Nova desc", "{}", true);
        when(toolService.update(eq("calculator"), any())).thenReturn(atualizada);

        mockMvc.perform(put("/tools/calculator")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(atualizada)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.description").value("Nova desc"));
    }

    @Test
    void PUT_toolInexistente_retorna404() throws Exception {
        when(toolService.update(eq("xyz"), any()))
            .thenThrow(new IllegalArgumentException("Tool not found: xyz"));

        mockMvc.perform(put("/tools/xyz")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new Tool("xyz", "desc", "{}", false))))
            .andExpect(status().isNotFound());
    }
}
