package com.sementelivre.backend.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;

/**
 * Propriedade e comunidade cadastradas pelo front-app: o endereço vai junto no
 * corpo (o app não tem como criar um logradouro antes) e cada proprietário só
 * vê e cria as próprias propriedades.
 */
class PropriedadeComunidadeIntegrationTest extends AbstractAuthIntegrationTest {

    private static final String ENDERECO = """
            {"logradouro": "Córrego Fundo, S/N", "municipio": "Mercês", "uf": "MG"}""";

    private String solicitarComunidade(String token) throws Exception {
        String resposta = mockMvc.perform(post("/api/comunidades")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome": "Comunidade %s", "endereco": %s}""".formatted(sufixoUnico(), ENDERECO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDENTE_APROVACAO"))
                .andExpect(jsonPath("$.logradouroMunicipio").value("Mercês"))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(resposta, "$.id");
    }

    private String propriedadeJson(String nome, String proprietarioId, String comunidadeId) {
        return """
                {"nome": "%s", "tamanhoHectares": 2.5, "proprietarioId": "%s", "comunidadeId": "%s", "endereco": %s}"""
                .formatted(nome, proprietarioId, comunidadeId, ENDERECO);
    }

    @Test
    void proprietarioCadastraPropriedadeComEnderecoEmComunidadeSolicitada() throws Exception {
        Conta eu = cadastrar();
        String token = login(eu.email(), SENHA);
        String comunidadeId = solicitarComunidade(token);

        mockMvc.perform(post("/api/propriedades")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(propriedadeJson("Sítio Integração", eu.id(), comunidadeId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comunidadeId").value(comunidadeId))
                .andExpect(jsonPath("$.logradouroUf").value("MG"))
                .andExpect(jsonPath("$.endereco.logradouro").value("Córrego Fundo, S/N"));

        mockMvc.perform(get("/api/propriedades").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nome").value("Sítio Integração"));
    }

    @Test
    void proprietarioSoVeEsoCriaAsPropriasPropriedades() throws Exception {
        Conta eu = cadastrar();
        Conta outro = cadastrar();
        String meuToken = login(eu.email(), SENHA);
        String tokenDoOutro = login(outro.email(), SENHA);
        String comunidadeId = solicitarComunidade(meuToken);

        mockMvc.perform(post("/api/propriedades")
                        .header(HttpHeaders.AUTHORIZATION, tokenDoOutro)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(propriedadeJson("Sítio do Outro", outro.id(), comunidadeId)))
                .andExpect(status().isOk());

        // Pedir a lista de outra pessoa devolve só as minhas (nenhuma)
        mockMvc.perform(get("/api/propriedades?proprietarioId=" + outro.id())
                        .header(HttpHeaders.AUTHORIZATION, meuToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // Cadastrar em nome de outra pessoa é proibido
        mockMvc.perform(post("/api/propriedades")
                        .header(HttpHeaders.AUTHORIZATION, meuToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(propriedadeJson("Sítio Alheio", outro.id(), comunidadeId)))
                .andExpect(status().isForbidden());

        // O admin filtra por proprietário
        mockMvc.perform(get("/api/propriedades?proprietarioId=" + outro.id())
                        .header(HttpHeaders.AUTHORIZATION, loginAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nome").value("Sítio do Outro"));
    }

    @Test
    void propriedadeSemEnderecoNemLogradouroIdEhRecusada() throws Exception {
        Conta eu = cadastrar();
        String token = login(eu.email(), SENHA);
        String comunidadeId = solicitarComunidade(token);

        mockMvc.perform(post("/api/propriedades")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome": "Sem Endereço", "tamanhoHectares": 1, "proprietarioId": "%s", "comunidadeId": "%s"}"""
                                .formatted(eu.id(), comunidadeId)))
                .andExpect(status().isBadRequest());
    }
}
