package com.sementelivre.backend.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import com.sementelivre.backend.dto.CompradorDTO;
import com.sementelivre.backend.dto.ItemPedidoRequestDTO;
import com.sementelivre.backend.dto.PedidoRequestDTO;
import com.sementelivre.backend.dto.PedidoResponseDTO;
import com.sementelivre.backend.entity.Produto;
import com.sementelivre.backend.entity.enums.EspecieGeral;
import com.sementelivre.backend.entity.enums.FormatoProduto;
import com.sementelivre.backend.entity.enums.TipoPedido;
import com.sementelivre.backend.entity.enums.TipoProduto;
import com.sementelivre.backend.repository.ProdutoRepository;
import com.sementelivre.backend.service.PedidoService;

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

    @Autowired
    private PedidoService pedidoService;

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

        // Pela API o produtor só registra pedido no próprio estoque; um pedido em que
        // outra conta é a solicitante vem de antes dessa regra, então nasce pelo service.
        PedidoResponseDTO pedido = pedidoService.criar(new PedidoRequestDTO(
                TipoPedido.VENDA, null, UUID.fromString(comprador.id()), UUID.fromString(vendedor.id()),
                List.of(new ItemPedidoRequestDTO(produto.getId(), 1.0, 10.0)),
                new CompradorDTO("Comprador Exclusão", "32977776666")));

        return new Cenario(vendedor, tokenVendedor, comprador, tokenComprador, pedido.id().toString());
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

        // O comprador continua com a conta (o pedido que envolvia a pessoa excluída já saiu acima).
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
