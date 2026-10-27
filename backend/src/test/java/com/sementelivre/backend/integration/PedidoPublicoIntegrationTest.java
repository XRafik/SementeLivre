package com.sementelivre.backend.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import com.sementelivre.backend.BackendApplication;
import com.sementelivre.backend.dto.PedidoPublicoRequestDTO;
import com.sementelivre.backend.entity.Estoque;
import com.sementelivre.backend.entity.Pedido;
import com.sementelivre.backend.entity.enums.StatusPedido;
import com.sementelivre.backend.entity.enums.TipoPedido;
import com.sementelivre.backend.repository.EstoqueRepository;
import com.sementelivre.backend.repository.PedidoRepository;
import com.sementelivre.backend.repository.ProdutoRepository;
import com.sementelivre.backend.exception.EstoqueInsuficienteException;
import com.sementelivre.backend.exception.TransicaoStatusInvalidaException;
import com.sementelivre.backend.service.EstoqueService;
import com.sementelivre.backend.service.PedidoService;

import jakarta.persistence.EntityManager;

/**
 * Pedido do site público passando pela cadeia de segurança de verdade: sem
 * token, a requisição precisa ser aceita e o pedido gravado sem solicitante.
 */
@SpringBootTest(classes = BackendApplication.class)
@AutoConfigureMockMvc
class PedidoPublicoIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private EstoqueRepository estoqueRepository;

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private EstoqueService estoqueService;

    @Autowired
    private PedidoService pedidoService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void visitanteSemLoginDeveRegistrarPedidoEReservarEstoque() throws Exception {
        CrossDomainFixture.Scenario scenario = cenarioComEstoque(100.0);
        UUID estoqueId = estoque(scenario).getId();

        String resposta = mockMvc.perform(post("/catalogo/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"estoqueId": "%s", "quantidade": 3, "nome": "Ana Souza",
                                 "email": "ana@exemplo.com", "telefone": "31 90000-0000",
                                 "mensagemOpcional": "Retiro na feira"}
                                """.formatted(estoqueId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDENTE"))
                .andExpect(jsonPath("$.tipoPedido").value("VENDA"))
                .andExpect(jsonPath("$.produto").value(scenario.produto().getNomePopular()))
                .andReturn().getResponse().getContentAsString();

        UUID pedidoId = UUID.fromString(resposta.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1"));

        try {
            transactionTemplate.executeWithoutResult(status -> {
                Pedido pedido = pedidoRepository.findByIdComItens(pedidoId).orElseThrow();

                assertNull(pedido.getUsuarioSolicitante());
                assertEquals(scenario.proprietario().getId(), pedido.getProprietarioRecebedor().getId());
                assertEquals(TipoPedido.VENDA, pedido.getTipoPedido());
                assertEquals("ana@exemplo.com", pedido.getComprador().getEmail());
                assertEquals(10.0, pedido.getItens().get(0).getPrecoUnitario());
                // Entrou na fila: o estoque so baixa quando o produtor confirmar (#183)
                assertEquals(100.0, estoque(scenario).getQuantidade());
            });
        } finally {
            // O pedido foi commitado no banco compartilhado; outros testes contam pedidos
            pedidoService.excluir(pedidoId);
        }
    }

    @Test
    void pedidoPublicoSemEmailDeveSerRecusado() throws Exception {
        CrossDomainFixture.Scenario scenario = cenarioComEstoque(100.0);

        mockMvc.perform(post("/catalogo/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"estoqueId": "%s", "quantidade": 3, "nome": "Ana Souza"}
                                """.formatted(estoque(scenario).getId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pedidoPublicoAcimaDoEstoqueDeveSerRecusado() throws Exception {
        CrossDomainFixture.Scenario scenario = cenarioComEstoque(2.0);

        mockMvc.perform(post("/catalogo/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"estoqueId": "%s", "quantidade": 3, "nome": "Ana Souza",
                                 "email": "ana@exemplo.com"}
                                """.formatted(estoque(scenario).getId())))
                .andExpect(status().is4xxClientError());

        assertEquals(2.0, estoque(scenario).getQuantidade());
    }

    /**
     * Fila (#183): pedidos simultâneos entram todos, sem baixar estoque. As
     * confirmações simultâneas é que não podem vender mais do que há: cada uma
     * trava o estoque antes do pedido, e quem não couber é recusado.
     */
    @Test
    void pedidosSimultaneosEntramNaFilaEAsConfirmacoesNaoPassamDoEstoque() throws Exception {
        CrossDomainFixture.Scenario scenario = cenarioComEstoque(3.0);
        UUID estoqueId = estoque(scenario).getId();

        int pedidos = 6;
        ExecutorService pool = Executors.newFixedThreadPool(pedidos);
        CountDownLatch largada = new CountDownLatch(1);
        ConcurrentLinkedQueue<UUID> aceitos = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<Throwable> recusados = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < pedidos; i++) {
            pool.submit(() -> {
                largada.await();
                try {
                    aceitos.add(pedidoService.criarPublico(new PedidoPublicoRequestDTO(
                            estoqueId, 1.0, "Corrida", "corrida@exemplo.com", null, null)).id());
                } catch (Throwable e) {
                    recusados.add(e);
                }
                return null;
            });
        }
        largada.countDown();
        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.SECONDS);

        try {
            assertEquals(pedidos, aceitos.size(), () -> "recusados: " + recusados);
            assertEquals(3.0, estoque(scenario).getQuantidade());

            ExecutorService confirmacoes = Executors.newFixedThreadPool(pedidos);
            CountDownLatch juntos = new CountDownLatch(1);
            ConcurrentLinkedQueue<UUID> confirmados = new ConcurrentLinkedQueue<>();
            ConcurrentLinkedQueue<Throwable> barrados = new ConcurrentLinkedQueue<>();
            for (UUID pedidoId : aceitos) {
                confirmacoes.submit(() -> {
                    juntos.await();
                    try {
                        pedidoService.confirmar(pedidoId);
                        confirmados.add(pedidoId);
                    } catch (Throwable e) {
                        barrados.add(e);
                    }
                    return null;
                });
            }
            juntos.countDown();
            confirmacoes.shutdown();
            confirmacoes.awaitTermination(30, TimeUnit.SECONDS);

            assertEquals(3, confirmados.size(), () -> "barrados: " + barrados);
            // Os outros já tinham sido recusados pela fila quando chegou a vez deles
            barrados.forEach(e -> assertTrue(e instanceof TransicaoStatusInvalidaException
                    || e instanceof EstoqueInsuficienteException, e.toString()));
            assertEquals(0.0, estoque(scenario).getQuantidade());
            assertEquals(3, aceitos.stream()
                    .filter(id -> pedidoRepository.findById(id).orElseThrow().getStatus() == StatusPedido.CANCELADO)
                    .count());
        } finally {
            List.copyOf(aceitos).forEach(pedidoService::excluir);
        }
    }

    // NEGOCIAÇÃO

    @Test
    void ofertaSemNegociacaoSoAceitaOProprioTipo() throws Exception {
        CrossDomainFixture.Scenario scenario = cenarioComEstoque(100.0);
        UUID estoqueId = estoque(scenario).getId();
        transactionTemplate.executeWithoutResult(status ->
                estoqueRepository.findById(estoqueId).orElseThrow().setAceitaNegociacao(false));

        mockMvc.perform(get("/catalogo/produtos/" + scenario.produto().getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ofertas[0].aceitaNegociacao").value(false));

        mockMvc.perform(pedidoComTipo(estoqueId, "TROCA", "198.51.100.30"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(
                        "O produtor não aceita negociar esta oferta: o pedido precisa ser de venda."));
        assertEquals(100.0, estoque(scenario).getQuantidade());

        String resposta = mockMvc.perform(pedidoComTipo(estoqueId, "VENDA", "198.51.100.31"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        pedidoService.excluir(UUID.fromString(resposta.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1")));
    }

    @Test
    void ofertaQueAceitaNegociarRegistraOTipoProposto() throws Exception {
        CrossDomainFixture.Scenario scenario = cenarioComEstoque(100.0);
        UUID estoqueId = estoque(scenario).getId();

        String resposta = mockMvc.perform(pedidoComTipo(estoqueId, "DOACAO", "198.51.100.32"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipoPedido").value("DOACAO"))
                .andReturn().getResponse().getContentAsString();
        pedidoService.excluir(UUID.fromString(resposta.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1")));
    }

    private MockHttpServletRequestBuilder pedidoComTipo(UUID estoqueId, String tipo, String ipDoVisitante) {
        return post("/catalogo/pedidos")
                .header("X-Forwarded-For", ipDoVisitante)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"estoqueId": "%s", "quantidade": 1, "nome": "Teste Negociação",
                         "email": "negociacao-%s@exemplo.com", "tipoPedido": "%s"}
                        """.formatted(estoqueId, UUID.randomUUID(), tipo));
    }

    // LIMITE (cada teste usa IPs e e-mails próprios: o limitador é compartilhado no contexto)

    @Test
    void quartoPedidoParaOMesmoEmailDeveSerBarradoCom429() throws Exception {
        CrossDomainFixture.Scenario scenario = cenarioComEstoque(100.0);
        UUID estoqueId = estoque(scenario).getId();
        String email = "vitima-" + UUID.randomUUID() + "@exemplo.com";

        // Variações do mesmo endereço e IPs diferentes: o limite é da caixa de entrada
        String[] variacoes = { email, email.toUpperCase(), email.replace("@", "+spam@") };
        UUID semOferta = UUID.randomUUID();
        for (int i = 0; i < variacoes.length; i++) {
            mockMvc.perform(pedido(semOferta, variacoes[i], "198.51.100." + (10 + i)))
                    .andExpect(status().isNotFound()); // oferta inexistente, mas a tentativa conta
        }

        // O quarto, numa oferta real, é barrado antes de chegar ao estoque
        mockMvc.perform(pedido(estoqueId, email, "198.51.100.13"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "3600"))
                .andExpect(jsonPath("$.message").value("Este e-mail já recebeu muitos pedidos. Tente novamente em 60 minutos."));

        assertEquals(100.0, estoque(scenario).getQuantidade());
    }

    @Test
    void decimoPrimeiroPedidoDoMesmoIpDeveSerBarrado() throws Exception {
        UUID semOferta = UUID.randomUUID();
        String ip = "198.51.100." + (100 + (int) (Math.random() * 100));

        for (int i = 0; i < 10; i++) {
            mockMvc.perform(pedido(semOferta, "ip-" + UUID.randomUUID() + "@exemplo.com", ip))
                    .andExpect(status().isNotFound());
        }

        mockMvc.perform(pedido(semOferta, "ip-" + UUID.randomUUID() + "@exemplo.com", ip))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void xForwardedForForjadoDiretoNoBackendNaoTrocaOIp() throws Exception {
        UUID semOferta = UUID.randomUUID();
        String ipReal = "203.0.113." + (1 + (int) (Math.random() * 200));

        // Fora da rede de proxies confiáveis: cada requisição forja um IP diferente
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(pedido(semOferta, "forjado-" + UUID.randomUUID() + "@exemplo.com", "10.9.9." + i)
                            .with(r -> { r.setRemoteAddr(ipReal); return r; }))
                    .andExpect(status().isNotFound());
        }

        mockMvc.perform(pedido(semOferta, "forjado-" + UUID.randomUUID() + "@exemplo.com", "10.9.9.99")
                        .with(r -> { r.setRemoteAddr(ipReal); return r; }))
                .andExpect(status().isTooManyRequests());
    }

    private MockHttpServletRequestBuilder pedido(UUID estoqueId, String email, String ipDoVisitante) {
        return post("/catalogo/pedidos")
                .header("X-Forwarded-For", ipDoVisitante)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"estoqueId": "%s", "quantidade": 1, "nome": "Teste Limite", "email": "%s"}
                        """.formatted(estoqueId, email));
    }

    // As requisições do MockMvc commitam: o cenário precisa estar commitado antes
    private CrossDomainFixture.Scenario cenarioComEstoque(double quantidade) {
        return transactionTemplate.execute(status ->
                CrossDomainFixture.create(entityManager, produtoRepository, estoqueService, quantidade));
    }

    private Estoque estoque(CrossDomainFixture.Scenario scenario) {
        return estoqueRepository
                .findByProprietarioIdAndProdutoId(scenario.proprietario().getId(), scenario.produto().getId())
                .orElseThrow();
    }
}
