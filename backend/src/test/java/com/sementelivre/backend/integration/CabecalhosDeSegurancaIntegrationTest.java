package com.sementelivre.backend.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.RequestBuilder;

/** Cabeçalhos de segurança da API (#202). */
class CabecalhosDeSegurancaIntegrationTest extends AbstractAuthIntegrationTest {

    @Test
    void respostasDaApiDevemTrazerOsCabecalhosDeSeguranca() throws Exception {
        String token = login(cadastrar().email(), SENHA);

        for (RequestBuilder requisicao : new RequestBuilder[] {
                get("/auth/me"),
                get("/auth/me").header(HttpHeaders.AUTHORIZATION, token) }) {
            mockMvc.perform(requisicao)
                    .andExpect(header().string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"))
                    .andExpect(header().string("Referrer-Policy", "no-referrer"))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                    .andExpect(header().string("X-Frame-Options", "DENY"));
        }
    }

    @Test
    void swaggerUiNaoDeveReceberACspDaApi() throws Exception {
        // O Swagger UI é uma página com script; com default-src 'none' ele não abriria no dev
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Content-Security-Policy"));
    }
}
