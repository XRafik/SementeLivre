package com.sementelivre.backend.service;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sementelivre.backend.entity.Comprador;
import com.sementelivre.backend.entity.Itens;
import com.sementelivre.backend.entity.Notificacao;
import com.sementelivre.backend.entity.Pedido;
import com.sementelivre.backend.entity.Produto;
import com.sementelivre.backend.entity.Proprietario;
import com.sementelivre.backend.entity.Usuario;
import com.sementelivre.backend.entity.enums.TipoPedido;
import com.sementelivre.backend.repository.NotificacaoRepository;

import jakarta.persistence.EntityManager;

// Testes das notificacoes automaticas do pedido (CDU-26).
// Separados do NotificacaoServiceTest para o arquivo nao passar de 300 linhas.
@ExtendWith(MockitoExtension.class)
class NotificacaoPedidoServiceTest {

    @Mock
    private NotificacaoRepository notificacaoRepository;

    @Mock
    private EntityManager entityManager;

    private NotificacaoService notificacaoService;

    private Proprietario proprietario;
    private Notificacao notificacao;

    @BeforeEach
    void setUp() {
        notificacaoService = new NotificacaoService(
                notificacaoRepository,
                entityManager
        );

        proprietario = new Proprietario();
        proprietario.setId(UUID.randomUUID());

        notificacao = Notificacao.builder()
                .id(UUID.randomUUID())
                .titulo("Novo pedido recebido")
                .mensagem("Voce recebeu um pedido.")
                .proprietario(proprietario)
                .build();
    }

    // ---- CDU-26: notificacao automatica quando o pedido e confirmado (#69) ----

    /** Monta um pedido confirmado com 1 solicitante e 2 itens. */
    private Pedido pedidoConfirmado() {

        Usuario solicitante = mock(Usuario.class);
        when(solicitante.getNome()).thenReturn("Maria Silva");

        Produto feijao = Produto.builder().nomePopular("Feijao Crioulo").build();
        Produto milho = Produto.builder().nomePopular("Milho").build();

        return Pedido.builder()
                .id(UUID.randomUUID())
                .tipoPedido(TipoPedido.TROCA)
                .usuarioSolicitante(solicitante)
                .proprietarioRecebedor(proprietario)
                .itens(List.of(
                        Itens.builder().produto(feijao).quantidade(2.0).build(),
                        Itens.builder().produto(milho).quantidade(1.5).build()))
                .build();
    }

    /** Captura a notificacao que o service mandou salvar. */
    private Notificacao notificacaoSalva(Pedido pedido) {
        return notificacaoSalva(() -> notificacaoService.criarParaPedidoConfirmado(pedido));
    }

    private Notificacao notificacaoSalva(Runnable gerarNotificacao) {

        ArgumentCaptor<Notificacao> captor = ArgumentCaptor.forClass(Notificacao.class);

        when(notificacaoRepository.saveAndFlush(captor.capture())).thenReturn(notificacao);

        gerarNotificacao.run();

        return captor.getValue();
    }

    @Test
    void mensagemDoPedidoConfirmadoDeveTerTipoSolicitanteEItens() {
        Pedido pedido = pedidoConfirmado();

        String mensagem = notificacaoSalva(pedido).getMensagem();

        assertTrue(mensagem.contains("Pedido de TROCA confirmado"), mensagem);
        assertTrue(mensagem.contains("Solicitante: Maria Silva"), mensagem);
        assertTrue(mensagem.contains("Feijao Crioulo"), mensagem);
        assertTrue(mensagem.contains("Milho"), mensagem);
    }

    @Test
    void mensagemDeveMostrarQuantidadeSemCasaDecimalDesnecessaria() {
        Pedido pedido = pedidoConfirmado();

        String mensagem = notificacaoSalva(pedido).getMensagem();

        assertTrue(mensagem.contains("2 x Feijao Crioulo"), mensagem);
        assertTrue(mensagem.contains("1.5 x Milho"), mensagem);
    }

    @Test
    void notificacaoDoPedidoConfirmadoDeveManterTituloEDestinatario() {
        Pedido pedido = pedidoConfirmado();

        Notificacao salva = notificacaoSalva(pedido);

        // O titulo e usado pelo teste de integracao do Dev 8: nao pode mudar.
        assertEquals("Pedido confirmado", salva.getTitulo());
        assertFalse(salva.isLida());
    }

    @Test
    void mensagemDeveFuncionarQuandoPedidoNaoTemItens() {
        Pedido pedido = Pedido.builder()
                .id(UUID.randomUUID())
                .tipoPedido(TipoPedido.DOACAO)
                .proprietarioRecebedor(proprietario)
                .itens(List.of())
                .build();

        String mensagem = notificacaoSalva(pedido).getMensagem();

        assertEquals("Pedido de DOACAO confirmado.", mensagem);
    }

    // ---- CDU-26: registro e cancelamento tambem avisam o proprietario (#94, #102) ----

    @Test
    void notificacaoDoPedidoRegistradoDeveTerTituloEDetalhes() {
        Pedido pedido = pedidoConfirmado();

        Notificacao salva = notificacaoSalva(() -> notificacaoService.criarParaPedidoRegistrado(pedido));

        assertEquals("Novo pedido recebido", salva.getTitulo());
        assertEquals("Pedido de TROCA registrado. Solicitante: Maria Silva."
                + " Itens: 2 x Feijao Crioulo, 1.5 x Milho.", salva.getMensagem());
        assertFalse(salva.isLida());
    }

    @Test
    void notificacaoDoPedidoCanceladoDeveAvisarDevolucaoDoEstoque() {
        Pedido pedido = pedidoConfirmado();

        Notificacao salva = notificacaoSalva(() -> notificacaoService.criarParaPedidoCancelado(pedido, true));

        assertEquals("Pedido cancelado", salva.getTitulo());
        assertTrue(salva.getMensagem().startsWith("Pedido de TROCA cancelado."), salva.getMensagem());
        assertTrue(salva.getMensagem().endsWith("A quantidade voltou para o estoque."), salva.getMensagem());
    }

    @Test
    void cancelamentoDePedidoNaFilaNaoFalaEmDevolucao() {
        Pedido pedido = pedidoConfirmado();

        Notificacao salva = notificacaoSalva(() -> notificacaoService.criarParaPedidoCancelado(pedido, false));

        assertFalse(salva.getMensagem().contains("estoque"), salva.getMensagem());
    }

    @Test
    void recusaDaFilaAvisaProdutorEQuemPediu() {
        Pedido pedido = pedidoConfirmado();
        Proprietario solicitante = solicitanteProprietario(pedido);

        List<Notificacao> salvas = notificacoesSalvas(
                () -> notificacaoService.criarParaPedidoRecusado(pedido), 2);

        assertEquals("Pedido recusado", salvas.get(0).getTitulo());
        assertTrue(salvas.get(0).getMensagem().startsWith("Pedido de TROCA recusado."), salvas.get(0).getMensagem());
        assertTrue(salvas.get(0).getMensagem().endsWith("O estoque não cobre mais este pedido depois da confirmação de outro."));
        assertEquals(solicitante, salvas.get(1).getProprietario());
        assertEquals("Seu pedido foi recusado", salvas.get(1).getTitulo());
    }

    @Test
    void mensagemDeveMostrarCompradorQuandoInformado() {
        Pedido pedido = pedidoConfirmado();
        pedido.setComprador(Comprador.builder().nome("Associação Rural").build());

        String mensagem = notificacaoSalva(pedido).getMensagem();

        assertTrue(mensagem.contains("Comprador: Associação Rural."), mensagem);
    }

    // ---- Quem fez o pedido tambem e avisado na confirmacao e no cancelamento ----

    /** Faz o solicitante do pedido ser um proprietario que existe no banco. */
    private Proprietario solicitanteProprietario(Pedido pedido) {
        UUID solicitanteId = UUID.randomUUID();
        when(pedido.getUsuarioSolicitante().getId()).thenReturn(solicitanteId);

        Proprietario solicitante = new Proprietario();
        solicitante.setId(solicitanteId);
        when(entityManager.find(Proprietario.class, solicitanteId)).thenReturn(solicitante);

        return solicitante;
    }

    /** Captura todas as notificacoes que o service mandou salvar. */
    private List<Notificacao> notificacoesSalvas(Runnable gerarNotificacao, int quantidade) {

        ArgumentCaptor<Notificacao> captor = ArgumentCaptor.forClass(Notificacao.class);

        when(notificacaoRepository.saveAndFlush(captor.capture())).thenReturn(notificacao);

        gerarNotificacao.run();

        verify(notificacaoRepository, times(quantidade)).saveAndFlush(any(Notificacao.class));
        return captor.getAllValues();
    }

    @Test
    void confirmacaoDeveAvisarTambemQuemFezOPedido() {
        Pedido pedido = pedidoConfirmado();
        Proprietario solicitante = solicitanteProprietario(pedido);

        List<Notificacao> salvas = notificacoesSalvas(
                () -> notificacaoService.criarParaPedidoConfirmado(pedido), 2);

        assertEquals("Pedido confirmado", salvas.get(0).getTitulo());
        assertEquals(solicitante, salvas.get(1).getProprietario());
        assertEquals("Seu pedido foi confirmado", salvas.get(1).getTitulo());
    }

    @Test
    void cancelamentoDeveAvisarTambemQuemFezOPedido() {
        Pedido pedido = pedidoConfirmado();
        Proprietario solicitante = solicitanteProprietario(pedido);

        List<Notificacao> salvas = notificacoesSalvas(
                () -> notificacaoService.criarParaPedidoCancelado(pedido, true), 2);

        assertEquals(solicitante, salvas.get(1).getProprietario());
        assertEquals("Seu pedido foi cancelado", salvas.get(1).getTitulo());
        assertTrue(salvas.get(1).getMensagem().startsWith("Pedido de TROCA cancelado."));
    }

    @Test
    void naoDeveAvisarSolicitanteQueNaoEProprietario() {
        Pedido pedido = pedidoConfirmado();
        UUID solicitanteId = UUID.randomUUID();
        when(pedido.getUsuarioSolicitante().getId()).thenReturn(solicitanteId);
        when(entityManager.find(Proprietario.class, solicitanteId)).thenReturn(null);

        List<Notificacao> salvas = notificacoesSalvas(
                () -> notificacaoService.criarParaPedidoConfirmado(pedido), 1);

        assertEquals("Pedido confirmado", salvas.get(0).getTitulo());
    }

    @Test
    void naoDeveAvisarDuasVezesQuemPediuParaSiMesmo() {
        Pedido pedido = pedidoConfirmado();
        when(pedido.getUsuarioSolicitante().getId()).thenReturn(proprietario.getId());

        notificacoesSalvas(() -> notificacaoService.criarParaPedidoCancelado(pedido, true), 1);
    }
}
