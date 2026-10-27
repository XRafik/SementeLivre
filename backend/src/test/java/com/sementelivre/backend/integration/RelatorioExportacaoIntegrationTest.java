package com.sementelivre.backend.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Testes de integracao dos endpoints de relatorio (issue #95).
 *
 * <p>Sobem a aplicacao inteira contra o Postgres do Testcontainers e fazem a
 * requisicao com token JWT de verdade, para confirmar que o download funciona
 * junto com a seguranca do projeto.</p>
 */
class RelatorioExportacaoIntegrationTest extends AbstractAuthIntegrationTest {

    private static final String ESTOQUE_CSV = "/relatorios/estoque/csv";
    private static final String ESTOQUE_PDF = "/relatorios/estoque/pdf";
    private static final String PEDIDOS_CSV = "/relatorios/pedidos/csv";

    @Test
    void semTokenDeveResponder401() throws Exception {
        mockMvc.perform(get(ESTOQUE_CSV).param("proprietarioId", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void proprietarioNaoPodeBaixarRelatorioDeOutro() throws Exception {
        Conta conta = cadastrar();
        String token = login(conta.email(), SENHA);

        mockMvc.perform(get(ESTOQUE_CSV)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .param("proprietarioId", UUID.randomUUID().toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void deveBaixarOProprioRelatorioDeEstoqueEmCsv() throws Exception {
        Conta conta = cadastrar();
        String token = login(conta.email(), SENHA);

        MvcResult resultado = mockMvc.perform(get(ESTOQUE_CSV)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .param("proprietarioId", conta.id()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"relatorio-estoque.csv\""))
                .andReturn();

        String csv = resultado.getResponse().getContentAsString(StandardCharsets.UTF_8);

        // Proprietario recem-criado nao tem estoque: sai so o cabecalho, sem quebrar
        assertTrue(csv.contains("Produto"), csv);
        assertEquals(1, csv.strip().split("\n").length);
    }

    @Test
    void adminDeveBaixarRelatorioDeQualquerProprietarioEmPdf() throws Exception {
        String tokenAdmin = loginAdmin();

        MvcResult resultado = mockMvc.perform(get(ESTOQUE_PDF)
                        .header(HttpHeaders.AUTHORIZATION, tokenAdmin)
                        .param("proprietarioId", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"relatorio-estoque.pdf\""))
                .andReturn();

        byte[] pdf = resultado.getResponse().getContentAsByteArray();

        assertEquals("%PDF", new String(pdf, 0, 4, StandardCharsets.ISO_8859_1));
    }

    @Test
    void relatorioDePedidosDeveAceitarOsFiltrosDePeriodo() throws Exception {
        Conta conta = cadastrar();
        String token = login(conta.email(), SENHA);

        mockMvc.perform(get(PEDIDOS_CSV)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .param("proprietarioId", conta.id())
                        .param("dataInicio", "2026-01-01")
                        .param("dataFim", "2026-12-31")
                        .param("status", "CONFIRMADO"))
                .andExpect(status().isOk());
    }

    @Test
    void relatorioDePedidosDeveAceitarOFiltroDeSemente() throws Exception {
        Conta conta = cadastrar();
        String token = login(conta.email(), SENHA);

        mockMvc.perform(get(PEDIDOS_CSV)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .param("proprietarioId", conta.id())
                        .param("produtoId", UUID.randomUUID().toString()))
                .andExpect(status().isOk());
    }

    @Test
    void filtroComValorInvalidoDeveResponder400() throws Exception {
        Conta conta = cadastrar();
        String token = login(conta.email(), SENHA);

        mockMvc.perform(get(PEDIDOS_CSV)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .param("proprietarioId", conta.id())
                        .param("status", "NAO_EXISTE"))
                .andExpect(status().isBadRequest());
    }
}
