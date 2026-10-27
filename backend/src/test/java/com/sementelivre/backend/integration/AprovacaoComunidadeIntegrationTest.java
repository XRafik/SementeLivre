package com.sementelivre.backend.integration;

import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;
import com.sementelivre.backend.entity.Logradouro;
import com.sementelivre.backend.repository.LogradouroRepository;

/**
 * Fluxo de solicitação de comunidade: o proprietário solicita (nasce
 * PENDENTE_APROVACAO) e só o admin aprova, rejeita, edita ou exclui. Antes
 * qualquer conta logada aprovava a própria solicitação.
 */
class AprovacaoComunidadeIntegrationTest extends AbstractAuthIntegrationTest {

    @Autowired
    private LogradouroRepository logradouroRepository;

    private String logradouroId;

    @BeforeEach
    void criarLogradouro() {
        logradouroId = logradouroRepository.save(Logradouro.builder()
                .logradouro("Córrego Fundo").municipio("Mercês").uf("MG").build()).getId().toString();
    }

    private String comunidadeJson() {
        // nomes bem distintos entre si para não cair na regra de similaridade
        return """
                {"nome": "Comunidade %s", "logradouroId": "%s"}""".formatted(java.util.UUID.randomUUID(), logradouroId);
    }

    private String solicitar(String token) throws Exception {
        String resposta = mockMvc.perform(post("/api/comunidades")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(comunidadeJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDENTE_APROVACAO"))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(resposta, "$.id");
    }

    @Test
    void proprietarioSoSolicitaEConsulta() throws Exception {
        Conta conta = cadastrar();
        String token = login(conta.email(), SENHA);
        String id = solicitar(token);

        mockMvc.perform(patch("/api/comunidades/" + id + "/aprovar").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/comunidades/" + id + "/rejeitar").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/comunidades/" + id)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(comunidadeJson()))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/comunidades/" + id).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isForbidden());

        // continua pendente
        mockMvc.perform(get("/api/comunidades/" + id).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDENTE_APROVACAO"));
    }

    @Test
    void adminAprovaERejeitaAtualizandoStatusEData() throws Exception {
        Conta conta = cadastrar();
        String token = login(conta.email(), SENHA);
        String admin = loginAdmin();
        String aprovada = solicitar(token);
        String rejeitada = solicitar(token);

        mockMvc.perform(patch("/api/comunidades/" + aprovada + "/aprovar").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ATIVA"))
                .andExpect(jsonPath("$.dataAprovacao").value(notNullValue()));

        mockMvc.perform(patch("/api/comunidades/" + rejeitada + "/rejeitar").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJEITADA"))
                .andExpect(jsonPath("$.dataAprovacao").value(nullValue()));

        // só sai de PENDENTE_APROVACAO uma vez
        mockMvc.perform(patch("/api/comunidades/" + aprovada + "/rejeitar").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isConflict());
        mockMvc.perform(patch("/api/comunidades/" + rejeitada + "/aprovar").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isConflict());
    }
}
