package com.sementelivre.backend.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.sementelivre.backend.BackendApplication;
import com.sementelivre.backend.dto.CompradorDTO;
import com.sementelivre.backend.dto.MovimentacaoResponseDTO;
import com.sementelivre.backend.dto.PedidoRequestDTO;
import com.sementelivre.backend.dto.PedidoResponseDTO;
import com.sementelivre.backend.entity.Estoque;
import com.sementelivre.backend.entity.Notificacao;
import com.sementelivre.backend.entity.Pedido;
import com.sementelivre.backend.entity.enums.OrigemMovimentacao;
import com.sementelivre.backend.entity.enums.StatusPedido;
import com.sementelivre.backend.entity.enums.TipoMovimentacao;
import com.sementelivre.backend.repository.EstoqueRepository;
import com.sementelivre.backend.repository.PedidoRepository;
import com.sementelivre.backend.repository.ProdutoRepository;
import com.sementelivre.backend.exception.EstoqueInsuficienteException;
import com.sementelivre.backend.exception.TransicaoStatusInvalidaException;
import com.sementelivre.backend.repository.NotificacaoRepository;
import com.sementelivre.backend.service.EstoqueService;
import com.sementelivre.backend.service.PedidoService;

import jakarta.persistence.EntityManager;

@SpringBootTest(classes = BackendApplication.class)
class PedidoEstoqueIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final double ESTOQUE_INICIAL = 100.0;
    private static final double QUANTIDADE_PEDIDA = 20.0;

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
    private NotificacaoRepository notificacaoRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    @Transactional
    void registrarPedido_entraNaFilaSemMexerNoEstoque() {
        CrossDomainFixture.Scenario scenario = fixture(ESTOQUE_INICIAL);

        PedidoResponseDTO pedido = pedidoService.criar(CrossDomainFixture.pedido(scenario, QUANTIDADE_PEDIDA));
        entityManager.flush();
        entityManager.clear();

        Estoque estoque = estoque(scenario);
        Pedido persistido = pedidoRepository.findByIdComItens(pedido.id()).orElseThrow();

        assertEquals(ESTOQUE_INICIAL, estoque.getQuantidade());
        assertEquals(StatusPedido.PENDENTE, persistido.getStatus());
    }

    @Test
    @Transactional
    void confirmarPedido_deveDiminuirSaldoEstoque() {
        CrossDomainFixture.Scenario scenario = fixture(ESTOQUE_INICIAL);
        PedidoResponseDTO pedido = pedidoService.criar(CrossDomainFixture.pedido(scenario, QUANTIDADE_PEDIDA));

        pedidoService.confirmar(pedido.id());
        entityManager.flush();
        entityManager.clear();

        assertEquals(ESTOQUE_INICIAL - QUANTIDADE_PEDIDA, estoque(scenario).getQuantidade());
    }

    // Exemplos do #183: 50 kg e dois pedidos na fila

    @Test
    @Transactional
    void confirmarQuandoSobraOSuficiente_mantemOOutroNaFila() {
        CrossDomainFixture.Scenario scenario = fixture(50.0);
        UUID quarenta = pedidoService.criar(CrossDomainFixture.pedido(scenario, 40.0)).id();
        UUID dez = pedidoService.criar(CrossDomainFixture.pedido(scenario, 10.0)).id();

        pedidoService.confirmar(quarenta);
        entityManager.flush();
        entityManager.clear();

        assertEquals(10.0, estoque(scenario).getQuantidade());
        assertEquals(StatusPedido.PENDENTE, pedidoRepository.findById(dez).orElseThrow().getStatus());

        // E ele ainda pode ser confirmado com o que sobrou
        pedidoService.confirmar(dez);
        entityManager.flush();
        entityManager.clear();
        assertEquals(0.0, estoque(scenario).getQuantidade());
    }

    @Test
    @Transactional
    void confirmarQuandoNaoSobraOSuficiente_recusaOOutroEAvisa() {
        CrossDomainFixture.Scenario scenario = fixture(50.0);
        UUID quarentaENove = pedidoService.criar(CrossDomainFixture.pedido(scenario, 49.0)).id();
        UUID dez = pedidoService.criar(CrossDomainFixture.pedido(scenario, 10.0)).id();

        pedidoService.confirmar(quarentaENove);
        entityManager.flush();
        entityManager.clear();

        // A vitrine passa a mostrar 1 kg
        assertEquals(1.0, estoque(scenario).getQuantidade());
        assertEquals(StatusPedido.CANCELADO, pedidoRepository.findById(dez).orElseThrow().getStatus());
        assertTrue(titulosDasNotificacoes(scenario).contains("Pedido recusado"));
        assertThrows(TransicaoStatusInvalidaException.class, () -> pedidoService.confirmar(dez));
    }

    @Test
    @Transactional
    void cancelarPedidoPendente_naoMexeNoEstoque() {
        CrossDomainFixture.Scenario scenario = fixture(ESTOQUE_INICIAL);
        PedidoResponseDTO criado = pedidoService.criar(CrossDomainFixture.pedido(scenario, QUANTIDADE_PEDIDA));

        pedidoService.cancelar(criado.id());
        entityManager.flush();
        entityManager.clear();

        assertEquals(ESTOQUE_INICIAL, estoque(scenario).getQuantidade());
    }

    @Test
    @Transactional
    void cancelarPedidoConfirmado_deveRestaurarSaldoEstoque() {
        CrossDomainFixture.Scenario scenario = fixture(ESTOQUE_INICIAL);
        PedidoResponseDTO criado = pedidoService.criar(CrossDomainFixture.pedido(scenario, QUANTIDADE_PEDIDA));
        pedidoService.confirmar(criado.id());

        pedidoService.cancelar(criado.id());
        entityManager.flush();
        entityManager.clear();

        assertEquals(ESTOQUE_INICIAL, estoque(scenario).getQuantidade());
        assertEquals(StatusPedido.CANCELADO,
                pedidoRepository.findById(criado.id()).orElseThrow().getStatus());
    }

    @Test
    @Transactional
    void historicoDoEstoque_deveAcompanharPedidoECancelamento() {
        CrossDomainFixture.Scenario scenario = fixture(ESTOQUE_INICIAL);
        PedidoResponseDTO criado = pedidoService.criar(CrossDomainFixture.pedido(scenario, QUANTIDADE_PEDIDA));
        pedidoService.confirmar(criado.id());
        pedidoService.cancelar(criado.id());
        entityManager.flush();
        entityManager.clear();

        Estoque estoque = estoque(scenario);
        List<MovimentacaoResponseDTO> historico = estoqueService
                .historico(estoque.getId(), PageRequest.of(0, 10))
                .getContent();

        // Mais recente primeiro: devolução, saída na confirmação, cadastro (#185)
        assertEquals(3, historico.size());
        assertEquals(OrigemMovimentacao.DEVOLUCAO, historico.get(0).origem());
        assertEquals(TipoMovimentacao.ENTRADA, historico.get(0).tipo());
        assertEquals(OrigemMovimentacao.PEDIDO, historico.get(1).origem());
        assertEquals(TipoMovimentacao.SAIDA_VENDA, historico.get(1).tipo());
        assertEquals(ESTOQUE_INICIAL, historico.get(1).saldoAnterior());
        assertEquals(ESTOQUE_INICIAL - QUANTIDADE_PEDIDA, historico.get(1).saldoPosterior());
        assertEquals(OrigemMovimentacao.CADASTRO, historico.get(2).origem());

        // O saldo da linha mais recente é o saldo do estoque
        assertEquals(estoque.getQuantidade(), historico.get(0).saldoPosterior());
    }

    @Test
    @Transactional
    void estoqueInsuficiente_deveRejeitarPedidoSemAlterarDados() {
        CrossDomainFixture.Scenario scenario = fixture(10.0);

        assertThrows(EstoqueInsuficienteException.class,
                () -> pedidoService.criar(CrossDomainFixture.pedido(scenario, QUANTIDADE_PEDIDA)));
        entityManager.flush();
        entityManager.clear();

        assertEquals(10.0, estoque(scenario).getQuantidade());
        assertEquals(0, pedidoRepository.count());
    }

    @Test
    @Transactional
    void cancelarPedidoDuasVezes_deveFalharSemAlterarEstoque() {
        CrossDomainFixture.Scenario scenario = fixture(ESTOQUE_INICIAL);
        PedidoResponseDTO criado = pedidoService.criar(CrossDomainFixture.pedido(scenario, QUANTIDADE_PEDIDA));

        pedidoService.cancelar(criado.id());
        assertThrows(TransicaoStatusInvalidaException.class, () -> pedidoService.cancelar(criado.id()));
        entityManager.flush();
        entityManager.clear();

        assertEquals(ESTOQUE_INICIAL, estoque(scenario).getQuantidade());
        assertEquals(StatusPedido.CANCELADO,
                pedidoRepository.findById(criado.id()).orElseThrow().getStatus());
        // O segundo cancelamento falhou antes de notificar
        assertEquals(1, titulosDasNotificacoes(scenario).stream()
                .filter("Pedido cancelado"::equals).count());
    }

    /**
     * Sem transacao de teste: cada cancelamento roda na sua propria transacao,
     * em threads separadas, como duas requisicoes chegando juntas. O lock no
     * pedido faz a segunda esperar a primeira e enxergar o status CANCELADO.
     */
    @Test
    void cancelamentosSimultaneos_devemRestaurarEstoqueUmaUnicaVez() throws Exception {
        CrossDomainFixture.Scenario scenario = transactionTemplate.execute(status ->
                CrossDomainFixture.create(entityManager, produtoRepository, estoqueService, ESTOQUE_INICIAL));
        UUID pedidoId = pedidoService.criar(CrossDomainFixture.pedido(scenario, QUANTIDADE_PEDIDA)).id();

        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> cancelar = () -> {
                largada.await();
                try {
                    pedidoService.cancelar(pedidoId);
                    return true;
                } catch (TransicaoStatusInvalidaException e) {
                    return false;
                }
            };
            Future<Boolean> primeiro = executor.submit(cancelar);
            Future<Boolean> segundo = executor.submit(cancelar);
            largada.countDown();

            assertEquals(1, Stream.of(primeiro.get(), segundo.get()).filter(Boolean::booleanValue).count());
            assertEquals(ESTOQUE_INICIAL, estoque(scenario).getQuantidade());
        } finally {
            executor.shutdownNow();
            // Os demais testes contam pedidos; nao deixa este para tras
            transactionTemplate.executeWithoutResult(status -> pedidoRepository.deleteById(pedidoId));
        }
    }

    @Test
    @Transactional
    void excluirPedidoPendente_deveExcluirItensERestaurarEstoque() {
        CrossDomainFixture.Scenario scenario = fixture(ESTOQUE_INICIAL);
        PedidoResponseDTO criado = pedidoService.criar(CrossDomainFixture.pedido(scenario, QUANTIDADE_PEDIDA));

        pedidoService.excluir(criado.id());
        entityManager.flush();
        entityManager.clear();

        assertEquals(ESTOQUE_INICIAL, estoque(scenario).getQuantidade());
        assertEquals(0, pedidoRepository.count());
    }

    @Test
    void falhaAoRegistrarPedido_deveFazerRollbackDePedidoEEstoque() {
        CrossDomainFixture.Scenario scenario = transactionTemplate.execute(status ->
                CrossDomainFixture.create(entityManager, produtoRepository, estoqueService, ESTOQUE_INICIAL));

        assertThrows(RuntimeException.class, () -> transactionTemplate.executeWithoutResult(status -> {
            pedidoService.criar(CrossDomainFixture.pedido(scenario, QUANTIDADE_PEDIDA));
            throw new TestRollbackException();
        }));

        assertEquals(ESTOQUE_INICIAL, estoque(scenario).getQuantidade());
        assertEquals(0, pedidoRepository.count());
        assertEquals(List.of(), titulosDasNotificacoes(scenario));
    }

    @Test
    void falhaAoConfirmarPedido_deveFazerRollbackDePedidoEstoqueENotificacao() {
        CrossDomainFixture.Scenario scenario = transactionTemplate.execute(status -> {
            CrossDomainFixture.Scenario created = CrossDomainFixture.create(
                    entityManager, produtoRepository, estoqueService, ESTOQUE_INICIAL);
            pedidoService.criar(CrossDomainFixture.pedido(created, QUANTIDADE_PEDIDA));
            return created;
        });
        UUID pedidoId = pedidoRepository.findAll().stream().findFirst().orElseThrow().getId();

        assertThrows(RuntimeException.class, () -> transactionTemplate.executeWithoutResult(status -> {
            pedidoService.confirmar(pedidoId);
            throw new TestRollbackException();
        }));

        entityManager.clear();
        assertEquals(StatusPedido.PENDENTE, pedidoRepository.findById(pedidoId).orElseThrow().getStatus());
        // A baixa da confirmacao foi junto no rollback
        assertEquals(ESTOQUE_INICIAL, estoque(scenario).getQuantidade());
        // So sobra a notificacao do registro; a da confirmacao foi junto no rollback
        assertEquals(List.of("Novo pedido recebido"), titulosDasNotificacoes(scenario));
    }

    @Test
    @Transactional
    void registrarPedidoComComprador_devePersistirCompradorEDevolverNaBusca() {
        CrossDomainFixture.Scenario scenario = fixture(ESTOQUE_INICIAL);
        PedidoRequestDTO base = CrossDomainFixture.pedido(scenario, QUANTIDADE_PEDIDA);
        PedidoRequestDTO comComprador = new PedidoRequestDTO(
                base.tipoPedido(), base.mensagemOpcional(), base.usuarioSolicitanteId(),
                base.proprietarioRecebedorId(), base.itens(),
                new CompradorDTO("Associação de Agricultores", "32999990000"));

        PedidoResponseDTO criado = pedidoService.criar(comComprador);
        entityManager.flush();
        entityManager.clear();

        PedidoResponseDTO buscado = pedidoService.buscarPorId(criado.id());

        assertEquals("Associação de Agricultores", buscado.comprador().nome());
        assertEquals("32999990000", buscado.comprador().telefone());
        assertEquals(scenario.produto().getNomePopular(), buscado.itens().get(0).nomeProduto());
    }

    private static final class TestRollbackException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    private CrossDomainFixture.Scenario fixture(double quantidade) {
        return CrossDomainFixture.create(entityManager, produtoRepository, estoqueService, quantidade);
    }

    private Estoque estoque(CrossDomainFixture.Scenario scenario) {
        return estoqueRepository
                .findByProprietarioIdAndProdutoId(scenario.proprietario().getId(), scenario.produto().getId())
                .orElseThrow();
    }

    private List<String> titulosDasNotificacoes(CrossDomainFixture.Scenario scenario) {
        return notificacaoRepository
                .findByProprietarioIdOrderByDataGeracaoDesc(scenario.proprietario().getId())
                .stream()
                .map(Notificacao::getTitulo)
                .toList();
    }
}
