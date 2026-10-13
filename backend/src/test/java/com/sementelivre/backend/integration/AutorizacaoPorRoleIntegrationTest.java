package com.sementelivre.backend.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * Controle de acesso por role (issue #60): ADMIN acessa os endpoints restritos e
 * PROPRIETARIO recebe 403; sem token, 401. A exclusao de pessoas/contas so por admin
 * esta coberta em PerfilProprietarioIntegrationTest.
 */
class AutorizacaoPorRoleIntegrationTest extends AbstractAuthIntegrationTest {

    @Test
    void soAdminDeveAcessarEndpointsAdministrativos() throws Exception {
        Conta conta = cadastrar();
        String tokenProprietario = login(conta.email(), SENHA);
        String tokenAdmin = loginAdmin();

        mockMvc.perform(get("/admin/confirmacao"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/admin/confirmacao").header(HttpHeaders.AUTHORIZATION, tokenProprietario))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/confirmacao").header(HttpHeaders.AUTHORIZATION, tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));

        mockMvc.perform(get("/actuator"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator").header(HttpHeaders.AUTHORIZATION, tokenProprietario))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/actuator").header(HttpHeaders.AUTHORIZATION, tokenAdmin))
                .andExpect(status().isOk());
    }

    @Test
    void adminDeveEditarDadosDeQualquerPessoa() throws Exception {
        Conta conta = cadastrar();
        String tokenAdmin = loginAdmin();

        mockMvc.perform(put("/api/proprietarios/" + conta.id())
                        .header(HttpHeaders.AUTHORIZATION, tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(atualizacaoJson("Editado Pelo Admin", conta.email())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Editado Pelo Admin"));

        mockMvc.perform(put("/api/pessoas/" + conta.id())
                        .header(HttpHeaders.AUTHORIZATION, tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(atualizacaoJson("Editado De Novo", conta.email())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Editado De Novo"));
    }

    /**
     * Os GET de pessoas, usuários e proprietários devolvem CPF, e-mail e
     * endereço. Antes qualquer conta logada listava todo mundo (inclusive o
     * admin) e lia os dados de qualquer pessoa pelo id.
     */
    @Test
    void proprietarioNaoDeveVerDadosPessoaisDeOutros() throws Exception {
        Conta eu = cadastrar();
        Conta outro = cadastrar();
        String meuToken = login(eu.email(), SENHA);
        String tokenAdmin = loginAdmin();

        for (String recurso : new String[] { "/api/pessoas", "/api/usuarios", "/api/proprietarios" }) {
            mockMvc.perform(get(recurso).header(HttpHeaders.AUTHORIZATION, meuToken))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get(recurso + "/" + outro.id()).header(HttpHeaders.AUTHORIZATION, meuToken))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get(recurso + "/" + eu.id()).header(HttpHeaders.AUTHORIZATION, meuToken))
                    .andExpect(status().isOk());

            mockMvc.perform(get(recurso).header(HttpHeaders.AUTHORIZATION, tokenAdmin))
                    .andExpect(status().isOk());
            mockMvc.perform(get(recurso + "/" + outro.id()).header(HttpHeaders.AUTHORIZATION, tokenAdmin))
                    .andExpect(status().isOk());

            mockMvc.perform(get(recurso))
                    .andExpect(status().isUnauthorized());
        }
    }
}
