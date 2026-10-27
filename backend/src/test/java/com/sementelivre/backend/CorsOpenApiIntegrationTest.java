package com.sementelivre.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class CorsOpenApiIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    private JsonNode documentoOpenApi() throws Exception {
        String document = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(document);
    }

    @Test
    void devePublicarDocumentoOpenApiComMetadadosESchemas() throws Exception {
        JsonNode root = documentoOpenApi();
        JsonNode schemas = root.path("components").path("schemas");

        assertTrue(root.path("info").path("title").asText().contains("Semente Livre"));
        // Schemas gerados dos DTOs que os endpoints usam de fato
        assertTrue(schemas.has("ComunidadeResponseDTO"), schemas.toPrettyString());
        assertTrue(schemas.has("PropriedadeResponseDTO"));
        assertTrue(schemas.has("PedidoResponseDTO"));
        // UsuarioResponseDTO (resposta de /auth/me) era sobrescrito por um schema manual só com id, nome e e-mail
        JsonNode usuario = schemas.path("UsuarioResponseDTO").path("properties");
        for (String campo : new String[] { "id", "nome", "email", "tipoPessoa", "endereco", "roles" }) {
            assertTrue(usuario.has(campo), "UsuarioResponseDTO sem " + campo + ": " + usuario.toPrettyString());
        }
    }

    @Test
    void deveDocumentarAutenticacaoJwt() throws Exception {
        JsonNode root = documentoOpenApi();

        JsonNode bearer = root.path("components").path("securitySchemes").path("bearerAuth");
        assertEquals("http", bearer.path("type").asText());
        assertEquals("bearer", bearer.path("scheme").asText());
        assertEquals("JWT", bearer.path("bearerFormat").asText());
        assertTrue(root.path("security").toString().contains("bearerAuth"));
    }

    @Test
    void todoEndpointDeveEstarNumGrupoNomeado() throws Exception {
        JsonNode root = documentoOpenApi();
        List<String> semGrupo = new ArrayList<>();

        root.path("paths").properties().forEach(caminho ->
                caminho.getValue().properties().forEach(operacao -> {
                    JsonNode tags = operacao.getValue().path("tags");
                    // sem @Tag o springdoc usa o nome da classe ("pedido-controller")
                    if (tags.isEmpty() || tags.get(0).asText().endsWith("-controller")) {
                        semGrupo.add(operacao.getKey().toUpperCase() + " " + caminho.getKey());
                    }
                }));

        assertTrue(root.path("paths").size() > 30, "documento com poucos endpoints");
        assertTrue(semGrupo.isEmpty(), "endpoints sem @Tag: " + semGrupo);
    }

    @Test
    void deveDisponibilizarSwaggerUi() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }

    @Test
    void devePermitirCorsParaFrontApp() throws Exception {
        mockMvc.perform(options("/v3/api-docs")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization,Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"));
    }

    @Test
    void devePermitirCorsParaFrontSite() throws Exception {
        mockMvc.perform(options("/v3/api-docs")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3001")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3001"));
    }

    @Test
    void naoDevePermitirOrigemExterna() throws Exception {
        mockMvc.perform(get("/v3/api-docs")
                        .header(HttpHeaders.ORIGIN, "https://malicious.example"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }
}
