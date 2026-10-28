package com.sementelivre.backend.integration;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

/**
 * Estoque, pedido e produto são do produtor que os cadastrou (A01 do
 * relatório de segurança). Antes qualquer conta logada lia, alterava e
 * apagava os de outro produtor sabendo o id, e listava o histórico de pedidos
 * (com nome e contato de quem pediu) de qualquer um.
 */
class AcessoAosDadosDoProdutorIntegrationTest extends AbstractAuthIntegrationTest {

    private static String produtoJson(String nome) {
        return """
                {"nomePopular":"%s","nomeCientifico":"Teste","urlFoto":"/uploads/produtos/teste.png","tipo":"LEGUMINOSA",
                 "especie":"FEIJAO","formato":"SEMENTE"}
                """.formatted(nome);
    }

    private static String estoqueJson(String proprietarioId, String produtoId) {
        return """
                {"proprietarioId":"%s","produtoId":"%s","descricao":"Oferta","preco":10,
                 "quantidade":50,"tipoPesagem":"KG","disponibilidade":"PARA_VENDA",
                 "tipoMovimentacao":"ENTRADA"}
                """.formatted(proprietarioId, produtoId);
    }

    private static String pedidoJson(String recebedorId, String solicitanteId, String produtoId) {
        return """
                {"tipoPedido":"VENDA","usuarioSolicitanteId":"%s","proprietarioRecebedorId":"%s",
                 "itens":[{"produtoId":"%s","quantidade":5,"precoUnitario":10}],
                 "comprador":{"nome":"Comprador Teste"}}
                """.formatted(solicitanteId, recebedorId, produtoId);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder req, String token, String corpo) {
        return req.header(HttpHeaders.AUTHORIZATION, token).contentType(MediaType.APPLICATION_JSON).content(corpo);
    }

    private String criar(String url, String token, String corpo) throws Exception {
        String resposta = mockMvc.perform(json(post(url), token, corpo))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(resposta, "$.id");
    }

    private record Oferta(String produtoId, String estoqueId) {}

    private Oferta oferta(Conta dono, String token) throws Exception {
        String produtoId = criar("/produtos", token, produtoJson("Feijão " + sufixoUnico()));
        return new Oferta(produtoId, criar("/estoques", token, estoqueJson(dono.id(), produtoId)));
    }

    /** Outros testes contam pedido_t inteira no banco compartilhado: quem cria pedido apaga a conta no fim. */
    private void excluirConta(Conta conta, String token) throws Exception {
        mockMvc.perform(delete("/api/pessoas/" + conta.id() + "/conta").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNoContent());
    }

    @Test
    void produtorNaoDeveMexerNoEstoqueDeOutro() throws Exception {
        Conta dono = cadastrar();
        Conta outro = cadastrar();
        String tokenDono = login(dono.email(), SENHA);
        String tokenOutro = login(outro.email(), SENHA);
        Oferta oferta = oferta(dono, tokenDono);
        String estoque = "/estoques/" + oferta.estoqueId();

        mockMvc.perform(get(estoque).header(HttpHeaders.AUTHORIZATION, tokenOutro))
                .andExpect(status().isForbidden());
        mockMvc.perform(json(put(estoque), tokenOutro, estoqueJson(outro.id(), oferta.produtoId())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(estoque + "/movimentacoes").header(HttpHeaders.AUTHORIZATION, tokenOutro))
                .andExpect(status().isForbidden());
        mockMvc.perform(json(post(estoque + "/movimentacoes"), tokenOutro,
                        "{\"tipo\":\"SAIDA_VENDA\",\"quantidade\":50}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(estoque).header(HttpHeaders.AUTHORIZATION, tokenOutro))
                .andExpect(status().isForbidden());

        // A lista só traz os estoques de quem pediu
        mockMvc.perform(get("/estoques").header(HttpHeaders.AUTHORIZATION, tokenOutro))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", not(hasItem(oferta.estoqueId()))))
                .andExpect(jsonPath("$[*].proprietarioId", everyItem(is(outro.id()))));

        // O dono continua com acesso a tudo
        mockMvc.perform(get(estoque).header(HttpHeaders.AUTHORIZATION, tokenDono))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantidade").value(50.0));
        mockMvc.perform(get("/estoques").header(HttpHeaders.AUTHORIZATION, tokenDono))
                .andExpect(jsonPath("$[*].id", hasItem(oferta.estoqueId())));
        mockMvc.perform(json(put(estoque), tokenDono, estoqueJson(dono.id(), oferta.produtoId())))
                .andExpect(status().isOk());
        mockMvc.perform(get(estoque + "/movimentacoes").header(HttpHeaders.AUTHORIZATION, tokenDono))
                .andExpect(status().isOk());
    }

    @Test
    void estoqueDeveSerSempreDeQuemCadastra() throws Exception {
        Conta dono = cadastrar();
        Conta outro = cadastrar();
        String tokenDono = login(dono.email(), SENHA);
        String tokenOutro = login(outro.email(), SENHA);
        Oferta oferta = oferta(dono, tokenDono);
        String produtoDoOutro = criar("/produtos", tokenOutro, produtoJson("Milho " + sufixoUnico()));

        // Oferta em nome de outro produtor
        mockMvc.perform(json(post("/estoques"), tokenOutro, estoqueJson(dono.id(), produtoDoOutro)))
                .andExpect(status().isForbidden());
        // Passar a própria oferta para outro produtor
        mockMvc.perform(json(put("/estoques/" + oferta.estoqueId()), tokenDono,
                        estoqueJson(outro.id(), oferta.produtoId())))
                .andExpect(status().isForbidden());
        // Pendurar uma oferta no produto de outro produtor
        mockMvc.perform(json(post("/estoques"), tokenOutro, estoqueJson(outro.id(), oferta.produtoId())))
                .andExpect(status().isForbidden());
    }

    @Test
    void produtorNaoDeveAlterarNemApagarProdutoDeOutro() throws Exception {
        Conta dono = cadastrar();
        Conta outro = cadastrar();
        String tokenDono = login(dono.email(), SENHA);
        String tokenOutro = login(outro.email(), SENHA);
        Oferta oferta = oferta(dono, tokenDono);
        String produto = "/produtos/" + oferta.produtoId();

        mockMvc.perform(json(put(produto), tokenOutro, produtoJson("Vandalizado")))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(produto).header(HttpHeaders.AUTHORIZATION, tokenOutro))
                .andExpect(status().isForbidden());

        mockMvc.perform(json(put(produto), tokenDono, produtoJson("Feijão Renomeado")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nomePopular").value("Feijão Renomeado"));
        mockMvc.perform(json(put(produto), loginAdmin(), produtoJson("Feijão Pelo Admin")))
                .andExpect(status().isOk());
    }

    @Test
    void produtorDeveExcluirAOfertaEOProdutoQueCadastrou() throws Exception {
        Conta dono = cadastrar();
        String tokenDono = login(dono.email(), SENHA);
        Oferta oferta = oferta(dono, tokenDono);

        // Mesma ordem do app: primeiro o estoque, depois o produto
        mockMvc.perform(delete("/estoques/" + oferta.estoqueId()).header(HttpHeaders.AUTHORIZATION, tokenDono))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/produtos/" + oferta.produtoId()).header(HttpHeaders.AUTHORIZATION, tokenDono))
                .andExpect(status().isNoContent());
    }

    @Test
    void produtorNaoDeveVerNemMexerNosPedidosDeOutro() throws Exception {
        Conta dono = cadastrar();
        Conta outro = cadastrar();
        String tokenDono = login(dono.email(), SENHA);
        String tokenOutro = login(outro.email(), SENHA);
        Oferta oferta = oferta(dono, tokenDono);

        // O usuarioSolicitanteId do corpo é ignorado: quem registra é quem está logado
        String resposta = mockMvc.perform(json(post("/pedidos"), tokenDono,
                        pedidoJson(dono.id(), outro.id(), oferta.produtoId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuarioSolicitanteId").value(dono.id()))
                .andReturn().getResponse().getContentAsString();
        String pedido = "/pedidos/" + JsonPath.read(resposta, "$.id");

        mockMvc.perform(get("/pedidos").param("proprietarioId", dono.id())
                        .header(HttpHeaders.AUTHORIZATION, tokenOutro))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(pedido).header(HttpHeaders.AUTHORIZATION, tokenOutro))
                .andExpect(status().isForbidden());
        mockMvc.perform(json(put(pedido), tokenOutro,
                        "{\"tipoPedido\":\"VENDA\",\"itens\":[{\"produtoId\":\"" + oferta.produtoId()
                                + "\",\"quantidade\":1}]}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch(pedido + "/confirmar").header(HttpHeaders.AUTHORIZATION, tokenOutro))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch(pedido + "/cancelar").header(HttpHeaders.AUTHORIZATION, tokenOutro))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(pedido).header(HttpHeaders.AUTHORIZATION, tokenOutro))
                .andExpect(status().isForbidden());
        // Nem registrar pedido no estoque de outro produtor
        mockMvc.perform(json(post("/pedidos"), tokenOutro, pedidoJson(dono.id(), outro.id(), oferta.produtoId())))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/pedidos").param("proprietarioId", dono.id())
                        .header(HttpHeaders.AUTHORIZATION, tokenDono))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get(pedido).header(HttpHeaders.AUTHORIZATION, loginAdmin()))
                .andExpect(status().isOk());
        mockMvc.perform(patch(pedido + "/confirmar").header(HttpHeaders.AUTHORIZATION, tokenDono))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMADO"));

        excluirConta(dono, tokenDono);
    }

    @Test
    void produtoNoHistoricoDeOutroProdutorNaoDeveSerAlterado() throws Exception {
        Conta dono = cadastrar();
        String tokenDono = login(dono.email(), SENHA);
        Oferta oferta = oferta(dono, tokenDono);
        mockMvc.perform(json(post("/pedidos"), tokenDono, pedidoJson(dono.id(), dono.id(), oferta.produtoId())))
                .andExpect(status().isOk());

        // Outro produtor sem nenhuma oferta do produto continua barrado pelo pedido do dono
        mockMvc.perform(delete("/estoques/" + oferta.estoqueId()).header(HttpHeaders.AUTHORIZATION, tokenDono))
                .andExpect(status().isNoContent());
        Conta outro = cadastrar();
        mockMvc.perform(json(put("/produtos/" + oferta.produtoId()), login(outro.email(), SENHA),
                        produtoJson("Vandalizado")))
                .andExpect(status().isForbidden());

        excluirConta(dono, tokenDono);
    }
}
