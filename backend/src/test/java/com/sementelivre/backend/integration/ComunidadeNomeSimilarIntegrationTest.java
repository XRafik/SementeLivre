package com.sementelivre.backend.integration;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.sementelivre.backend.entity.Logradouro;
import com.sementelivre.backend.repository.LogradouroRepository;

/**
 * CDU-21: nome de comunidade parecido com um existente é recusado com 409 e uma
 * sugestão de nome. Antes a NomeSimilarException não tinha handler e virava 500.
 */
class ComunidadeNomeSimilarIntegrationTest extends AbstractAuthIntegrationTest {

    @Autowired
    private LogradouroRepository logradouroRepository;

    private String comunidadeJson(String nome, String logradouroId) {
        return """
                {"nome": "%s", "logradouroId": "%s"}""".formatted(nome, logradouroId);
    }

    @Test
    void nomeSimilarDeveRetornar409ComSugestao() throws Exception {
        Conta conta = cadastrar();
        String token = login(conta.email(), SENHA);
        String logradouroId = logradouroRepository.save(Logradouro.builder()
                .logradouro("Córrego Fundo").municipio("Mercês").uf("MG").build()).getId().toString();
        String nome = "Comunidade Vale " + sufixoUnico();

        mockMvc.perform(post("/api/comunidades")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(comunidadeJson(nome, logradouroId)))
                .andExpect(status().isOk());

        // Uma letra a menos e sem acento: continua "o mesmo nome"
        mockMvc.perform(post("/api/comunidades")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(comunidadeJson(nome.substring(0, nome.length() - 1), logradouroId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("muito similar")))
                .andExpect(jsonPath("$.message", containsString("Sugestão")))
                .andExpect(jsonPath("$.message", containsString("Mercês")))
                .andExpect(jsonPath("$.fieldErrors[0]", containsString("nome: ")));
    }
}
