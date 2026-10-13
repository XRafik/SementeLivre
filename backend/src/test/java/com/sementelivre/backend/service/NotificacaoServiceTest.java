package com.sementelivre.backend.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.sementelivre.backend.dto.NotificacaoRequestDTO;
import com.sementelivre.backend.dto.NotificacaoResponseDTO;
import com.sementelivre.backend.entity.Comprador;
import com.sementelivre.backend.entity.Itens;
import com.sementelivre.backend.entity.Notificacao;
import com.sementelivre.backend.entity.Pedido;
import com.sementelivre.backend.entity.Produto;
import com.sementelivre.backend.entity.Proprietario;
import com.sementelivre.backend.entity.Usuario;
import com.sementelivre.backend.entity.enums.TipoPedido;
import com.sementelivre.backend.exception.RecursoNaoEncontradoException;
import com.sementelivre.backend.repository.NotificacaoRepository;

import jakarta.persistence.EntityManager;

@ExtendWith(MockitoExtension.class)
class NotificacaoServiceTest {

    @Mock
    private NotificacaoRepository notificacaoRepository;

    @Mock
    private EntityManager entityManager;

    private NotificacaoService notificacaoService;

    private UUID notificacaoId;
    private UUID proprietarioId;

    private Proprietario proprietario;
    private Notificacao notificacao;
    private NotificacaoRequestDTO dto;

    @BeforeEach
    void setUp() {
        notificacaoService = new NotificacaoService(
                notificacaoRepository,
                entityManager
        );

        notificacaoId = UUID.randomUUID();
        proprietarioId = UUID.randomUUID();

        proprietario = new Proprietario();
        proprietario.setId(proprietarioId);

        notificacao = Notificacao.builder()
                .id(notificacaoId)
                .titulo("Novo pedido recebido")
                .mensagem("Voce recebeu um pedido.")
                .proprietario(proprietario)
                .build();

        dto = new NotificacaoRequestDTO(
                "Novo pedido recebido",
                "Voce recebeu um pedido.",
                proprietarioId,
                null
        );
    }

    @AfterEach
    void limparAutenticacao() {
        SecurityContextHolder.clearContext();
    }

    // Leitura, edição e exclusão só valem para o dono da notificação.
    private void autenticarComoDono() {
        Usuario dono = new Usuario();
        dono.setId(proprietarioId);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(dono, null, List.of()));
    }

    @Test
    void deveCriarNotificacao() {
        when(entityManager.find(Proprietario.class, proprietarioId)).thenReturn(proprietario);
        when(notificacaoRepository.saveAndFlush(any(Notificacao.class))).thenReturn(notificacao);

        NotificacaoResponseDTO resposta = notificacaoService.criar(dto);

        assertNotNull(resposta);
        assertEquals("Novo pedido recebido", resposta.titulo());
        assertEquals(proprietarioId, resposta.proprietarioId());
        assertFalse(resposta.lida());
    }

    @Test
    void naoDeveCriarNotificacaoSeProprietarioNaoExistir() {
        when(entityManager.find(Proprietario.class, proprietarioId)).thenReturn(null);

        assertThrows(RecursoNaoEncontradoException.class,
                () -> notificacaoService.criar(dto));

        verify(notificacaoRepository, never()).saveAndFlush(any(Notificacao.class));
    }

    @Test
    void deveBuscarNotificacaoPorId() {
        autenticarComoDono();
        when(notificacaoRepository.findById(notificacaoId)).thenReturn(Optional.of(notificacao));

        NotificacaoResponseDTO resposta = notificacaoService.buscarPorId(notificacaoId);

        assertEquals(notificacaoId, resposta.id());
    }

    @Test
    void deveLancarErroAoBuscarNotificacaoInexistente() {
        when(notificacaoRepository.findById(notificacaoId)).thenReturn(Optional.empty());

        assertThrows(RecursoNaoEncontradoException.class,
                () -> notificacaoService.buscarPorId(notificacaoId));
    }

    @Test
    void deveListarNotificacoes() {
        autenticarComoDono();
        when(notificacaoRepository.findByProprietarioIdOrderByDataGeracaoDesc(proprietarioId))
                .thenReturn(List.of(notificacao));

        List<NotificacaoResponseDTO> resposta = notificacaoService.listar();

        assertEquals(1, resposta.size());
    }

    @Test
    void deveAtualizarNotificacao() {
        NotificacaoRequestDTO novosDados = new NotificacaoRequestDTO(
                "Titulo atualizado",
                "Mensagem atualizada",
                proprietarioId,
                null
        );

        autenticarComoDono();
        when(notificacaoRepository.findById(notificacaoId)).thenReturn(Optional.of(notificacao));
        when(notificacaoRepository.save(notificacao)).thenReturn(notificacao);

        NotificacaoResponseDTO resposta = notificacaoService.atualizar(notificacaoId, novosDados);

        assertEquals("Titulo atualizado", resposta.titulo());
        assertEquals("Mensagem atualizada", resposta.mensagem());
    }

    @Test
    void deveMarcarNotificacaoComoLida() {
        autenticarComoDono();
        when(notificacaoRepository.findById(notificacaoId)).thenReturn(Optional.of(notificacao));
        when(notificacaoRepository.save(notificacao)).thenReturn(notificacao);

        NotificacaoResponseDTO resposta = notificacaoService.marcarComoLida(notificacaoId);

        assertTrue(resposta.lida());
        assertNotNull(resposta.dataLeitura());
    }

    @Test
    void deveDeletarNotificacao() {
        autenticarComoDono();
        when(notificacaoRepository.findById(notificacaoId)).thenReturn(Optional.of(notificacao));

        notificacaoService.deletar(notificacaoId);

        verify(notificacaoRepository).delete(notificacao);
    }

    @Test
    void naoDeveDeletarNotificacaoInexistente() {
        when(notificacaoRepository.findById(notificacaoId)).thenReturn(Optional.empty());

        assertThrows(RecursoNaoEncontradoException.class,
                () -> notificacaoService.deletar(notificacaoId));

        verify(notificacaoRepository, never()).delete(any(Notificacao.class));
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

        Notificacao salva = notificacaoSalva(() -> notificacaoService.criarParaPedidoCancelado(pedido));

        assertEquals("Pedido cancelado", salva.getTitulo());
        assertTrue(salva.getMensagem().startsWith("Pedido de TROCA cancelado."), salva.getMensagem());
        assertTrue(salva.getMensagem().endsWith("O estoque reservado foi devolvido."), salva.getMensagem());
    }

    @Test
    void mensagemDeveMostrarCompradorQuandoInformado() {
        Pedido pedido = pedidoConfirmado();
        pedido.setComprador(Comprador.builder().nome("Associação Rural").build());

        String mensagem = notificacaoSalva(pedido).getMensagem();

        assertTrue(mensagem.contains("Comprador: Associação Rural."), mensagem);
    }
}
