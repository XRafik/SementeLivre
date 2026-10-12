package com.sementelivre.backend.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;

/**
 * Divergências encontradas na validação do domínio de pessoas em ambiente de
 * produção (issue #133): exposição de CPF/RG entre proprietários, unicidade de
 * e-mail sem diferenciar maiúsculas e campos acima do tamanho das colunas.
 */
class DadosPessoaisIntegrationTest extends AbstractAuthIntegrationTest {

    @Test
    void proprietarioNaoDeveConsultarNemListarDadosDeOutraPessoa() throws Exception {
        Conta maria = cadastrar();
        Conta joao = cadastrar();
        String tokenMaria = login(maria.email(), SENHA);

        for (String rota : List.of("/api/pessoas", "/api/proprietarios", "/api/usuarios",
                "/api/pessoas/" + joao.id(), "/api/proprietarios/" + joao.id(), "/api/usuarios/" + joao.id())) {
            mockMvc.perform(get(rota).header(HttpHeaders.AUTHORIZATION, tokenMaria))
                    .andExpect(status().isForbidden());
        }

        for (String rota : List.of("/api/pessoas/", "/api/proprietarios/", "/api/usuarios/")) {
            mockMvc.perform(get(rota + maria.id()).header(HttpHeaders.AUTHORIZATION, tokenMaria))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.documento").value(maria.documento()));
        }

        String tokenAdmin = loginAdmin();
        mockMvc.perform(get("/api/pessoas").header(HttpHeaders.AUTHORIZATION, tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(joao.id())));
        mockMvc.perform(get("/api/proprietarios/" + joao.id()).header(HttpHeaders.AUTHORIZATION, tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rg").value(joao.rg()));
    }

    @Test
    void soAdminDeveCriarProprietarioPelaApi() throws Exception {
        Conta maria = cadastrar();
        String tokenMaria = login(maria.email(), SENHA);
        String sufixo = sufixoUnico();
        String novo = cadastroJson("api-" + sufixo + "@teste.com", cpfValido(), "MG-API-" + sufixo);

        mockMvc.perform(post("/api/proprietarios").header(HttpHeaders.AUTHORIZATION, tokenMaria)
                        .contentType(MediaType.APPLICATION_JSON).content(novo))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/proprietarios").header(HttpHeaders.AUTHORIZATION, loginAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content(novo))
                .andExpect(status().isCreated());
    }

    @Test
    void emailComOutraCaixaDeveSerOMesmoEmail() throws Exception {
        Conta maria = cadastrar();
        Conta joao = cadastrar();

        mockMvc.perform(post("/auth/cadastrar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cadastroJson(maria.email().toUpperCase(), cpfValido(), "RG-" + sufixoUnico())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.fieldErrors[0]").value("email: E-mail já cadastrado no sistema."));

        String tokenMaria = login(maria.email().toUpperCase(), SENHA);
        String perfil = mockMvc.perform(get("/auth/me").header(HttpHeaders.AUTHORIZATION, tokenMaria))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat((String) JsonPath.read(perfil, "$.id")).isEqualTo(maria.id());

        mockMvc.perform(put("/api/proprietarios/" + maria.id()).header(HttpHeaders.AUTHORIZATION, tokenMaria)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(atualizacaoJson("Maria", joao.email().toUpperCase())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.fieldErrors[0]").value("email: E-mail já cadastrado no sistema."));
    }

    @Test
    void emailDeveSerGravadoEmMinusculas() throws Exception {
        String sufixo = sufixoUnico();
        mockMvc.perform(post("/auth/cadastrar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cadastroJson("Caixa.Mista-" + sufixo + "@Teste.com", cpfValido(), "RG-" + sufixo)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("caixa.mista-" + sufixo + "@teste.com"));
    }

    @Test
    void campoAcimaDoTamanhoDaColunaDeveRetornar400NoCampo() throws Exception {
        String sufixo = sufixoUnico();
        String base = cadastroJson("tamanho-" + sufixo + "@teste.com", cpfValido(), "RG-" + sufixo);

        record Caso(String de, String para, String campo) {}
        List<Caso> casos = List.of(
                new Caso("\"nome\": \"Proprietario Perfil\"", "\"nome\": \"" + "N".repeat(151) + "\"", "nome"),
                new Caso("\"telefone\": \"32999998888\"", "\"telefone\": \"" + "3".repeat(16) + "\"", "telefone"),
                new Caso("\"municipio\": \"Rio Pomba\"", "\"municipio\": \"" + "M".repeat(101) + "\"", "endereco.municipio"),
                new Caso("\"numero\": \"10\"", "\"numero\": \"" + "1".repeat(11) + "\"", "endereco.numero"),
                new Caso("\"cep\": \"36180000\"", "\"cep\": \"3618000000\"", "endereco.cep"),
                new Caso("\"senha\": \"" + SENHA + "\"", "\"senha\": \"Ab1\"", "senha"));

        for (Caso caso : casos) {
            mockMvc.perform(post("/auth/cadastrar")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(base.replace(caso.de(), caso.para())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[0]", containsString(caso.campo() + ": ")));
        }
    }

    @Test
    void perfilPublicoNaoDeveExporDadosSensiveis() throws Exception {
        String sufixo = sufixoUnico();
        String email = "publico-" + sufixo + "@teste.com";
        String documento = cpfValido();
        String rg = "MG-PUB-" + sufixo;
        String corpo = cadastroJson(email, documento, rg).replace("\"senha\"", "\"exibirNoSitePublico\": true, \"senha\"");
        String resposta = mockMvc.perform(post("/auth/cadastrar")
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(resposta, "$.id");

        mockMvc.perform(get("/produtores/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Proprietario Perfil"))
                .andExpect(jsonPath("$.municipio").value("Rio Pomba"))
                .andExpect(content().string(not(containsString(documento))))
                .andExpect(content().string(not(containsString(rg))))
                .andExpect(content().string(not(containsString(email))))
                .andExpect(content().string(not(containsString("32999998888"))));
    }
}
