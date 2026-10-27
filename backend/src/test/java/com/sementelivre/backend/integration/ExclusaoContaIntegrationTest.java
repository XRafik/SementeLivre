package com.sementelivre.backend.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import com.jayway.jsonpath.JsonPath;
import com.sementelivre.backend.entity.Produto;
import com.sementelivre.backend.entity.enums.EspecieGeral;
import com.sementelivre.backend.entity.enums.FormatoProduto;
import com.sementelivre.backend.entity.enums.TipoProduto;
import com.sementelivre.backend.repository.ProdutoRepository;

/**
 * Exclusão de pessoa com dados vinculados (issue #133). As FKs para pessoa são
 * ON DELETE RESTRICT e itens_pedido_t.pedido_id também: na validação em
 * produção a exclusão de conta (LGPD) de quem tinha pedido falhava com 409, e
 * a exclusão administrativa respondia só "Violação de integridade nos dados.".
 */
class ExclusaoContaIntegrationTest extends AbstractAuthIntegrationTest {

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private record Cenario(Conta vendedor, String tokenVendedor, Conta comprador, String tokenComprador,
                           String pedidoId) {}

    /** Vendedor com estoque e um pedido feito pelo comprador, com dados de comprador. */
    private Cenario cenarioComPedido() throws Exception {
        Conta vendedor = cadastrar();
        Conta comprador = cadastrar();
        String tokenVendedor = login(vendedor.email(), SENHA);
        String tokenComprador = login(comprador.email(), SENHA);

        Produto produto = produtoRepository.saveAndFlush(Produto.builder()
                .nomePopular("Feijão Exclusão " + sufixoUnico())
                .nomeCientifico("Phaseolus vulgaris")
                .urlFoto("https://example.com/feijao.png")
                .tipo(TipoProduto.CEREAL)
                .especie(EspecieGeral.FEIJAO)
                .formato(FormatoProduto.SEMENTE)
                .dataInclusao(LocalDateTime.now())
                .dataUltimaAlteracao(LocalDateTime.now())
                .build());

        mockMvc.perform(post("/estoques").header(HttpHeaders.AUTHORIZATION, tokenVendedor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"proprietarioId": "%s", "produtoId": "%s", "descricao": "Estoque",
                                 "preco": 10.0, "quantidade": 50.0, "tipoPesagem": "KG",
                                 "disponibilidade": "PARA_VENDA", "tipoMovimentacao": "ENTRADA"}
                                """.formatted(vendedor.id(), produto.getId())))
                .andExpect(status().isOk());

        String pedido = mockMvc.perform(post("/pedidos").header(HttpHeaders.AUTHORIZATION, tokenComprador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipoPedido": "VENDA", "proprietarioRecebedorId": "%s",
                                 "itens": [{"produtoId": "%s", "quantidade": 1.0, "precoUnitario": 10.0}],
                                 "comprador": {"nome": "Comprador Exclusão", "telefone": "32977776666"}}
                                """.formatted(vendedor.id(), produto.getId())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return new Cenario(vendedor, tokenVendedor, comprador, tokenComprador, JsonPath.read(pedido, "$.id"));
    }

    private long contar(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Long.class, args);
    }

    @Test
    void titularComEstoqueEPedidoDeveExcluirAContaSemDeixarDadosPessoaisPraTras() throws Exception {
        Cenario c = cenarioComPedido();
        UUID vendedorId = UUID.fromString(c.vendedor().id());
        UUID enderecoId = jdbcTemplate.queryForObject(
                "SELECT logradouro_id FROM pessoa_t WHERE id = ?", UUID.class, vendedorId);
        UUID compradorId = jdbcTemplate.queryForObject(
                "SELECT comprador_id FROM pedido_t WHERE id = ?", UUID.class, UUID.fromString(c.pedidoId()));

        mockMvc.perform(delete("/api/pessoas/" + c.vendedor().id() + "/conta")
                        .header(HttpHeaders.AUTHORIZATION, c.tokenVendedor()))
                .andExpect(status().isNoContent());

        assertThat(contar("SELECT count(*) FROM pessoa_t WHERE id = ?", vendedorId)).isZero();
        assertThat(contar("SELECT count(*) FROM pedido_t WHERE id = ?", UUID.fromString(c.pedidoId()))).isZero();
        assertThat(contar("SELECT count(*) FROM estoque_t WHERE proprietario_id = ?", vendedorId)).isZero();
        assertThat(contar("SELECT count(*) FROM logradouro_t WHERE id = ?", enderecoId)).isZero();
        assertThat(contar("SELECT count(*) FROM comprador_t WHERE id = ?", compradorId)).isZero();

        // O comprador continua com a conta e sem o pedido que envolvia a pessoa excluída.
        mockMvc.perform(get("/pedidos/" + c.pedidoId()).header(HttpHeaders.AUTHORIZATION, c.tokenComprador()))
                .andExpect(status().isNotFound());
        login(c.comprador().email(), SENHA);
    }

    @Test
    void adminNaoDeveExcluirPessoaComVinculosEAContaContinuaIntegra() throws Exception {
        Cenario c = cenarioComPedido();

        mockMvc.perform(delete("/api/pessoas/" + c.vendedor().id()).header(HttpHeaders.AUTHORIZATION, loginAdmin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("1 estoque(s)")))
                .andExpect(jsonPath("$.message", containsString("1 pedido(s)")))
                .andExpect(jsonPath("$.message", containsString("exclusão de conta")));

        // Nada foi apagado pela metade: a conta segue logando e a exclusão pelo titular ainda funciona.
        // Também limpa o pedido, porque outros testes contam pedido_t inteira no banco compartilhado.
        login(c.vendedor().email(), SENHA);
        mockMvc.perform(delete("/api/pessoas/" + c.vendedor().id() + "/conta")
                        .header(HttpHeaders.AUTHORIZATION, c.tokenVendedor()))
                .andExpect(status().isNoContent());
    }
}
