package com.sementelivre.backend.integration;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Força bruta no login (#200): com a senha errada demais, o e-mail fica
 * bloqueado por um tempo, e o IP tem um teto de tentativas. Contexto próprio,
 * com limites baixos (o application-integration.yml desliga os limites).
 */
@TestPropertySource(properties = {
        "app.auth.limite.senha-errada-por-email.limite=3",
        "app.auth.limite.login-por-ip.limite=8"
})
class LimiteAutenticacaoIntegrationTest extends AbstractAuthIntegrationTest {

    private static MockHttpServletRequestBuilder loginDe(String ip, String email, String senha) {
        return post("/auth/login")
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson(email, senha));
    }

    @Test
    void senhaErradaDemaisBloqueiaAContaComRetryAfter() throws Exception {
        Conta conta = cadastrar();

        for (int i = 1; i <= 3; i++) {
            mockMvc.perform(loginDe("198.51.100." + i, conta.email(), "SenhaErrada1"))
                    .andExpect(status().isUnauthorized());
        }

        // Nem a senha certa entra até a janela passar, de outro IP também
        mockMvc.perform(loginDe("198.51.100.50", conta.email(), SENHA))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.message", startsWith("Muitas tentativas com senha errada para este e-mail")));
    }

    @Test
    void loginCertoAntesDoLimiteZeraAContagem() throws Exception {
        Conta conta = cadastrar();

        for (int rodada = 0; rodada < 2; rodada++) {
            mockMvc.perform(loginDe("198.51.100.60", conta.email(), "SenhaErrada1"))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(loginDe("198.51.100.60", conta.email(), "SenhaErrada1"))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(loginDe("198.51.100.60", conta.email(), SENHA))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void ipComTentativasDemaisRecebe429() throws Exception {
        String ip = "198.51.100.70";
        for (int i = 0; i < 8; i++) {
            mockMvc.perform(loginDe(ip, "ninguem" + i + "@teste.com", "SenhaErrada1"))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(loginDe(ip, "outro@teste.com", "SenhaErrada1"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message", startsWith("Muitas tentativas de login deste dispositivo")));
    }
}
