package com.sementelivre.backend.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.sementelivre.backend.dto.CompradorDTO;
import com.sementelivre.backend.dto.ItemPedidoRequestDTO;
import com.sementelivre.backend.dto.PedidoFiltroDTO;
import com.sementelivre.backend.dto.PedidoPublicoRequestDTO;
import com.sementelivre.backend.dto.PedidoPublicoResponseDTO;
import com.sementelivre.backend.dto.PedidoRequestDTO;
import com.sementelivre.backend.dto.PedidoResponseDTO;
import com.sementelivre.backend.dto.PedidoUpdateDTO;
import com.sementelivre.backend.entity.Comprador;
import com.sementelivre.backend.entity.Estoque;
import com.sementelivre.backend.entity.Itens;
import com.sementelivre.backend.entity.Pedido;
import com.sementelivre.backend.entity.Produto;
import com.sementelivre.backend.entity.Proprietario;
import com.sementelivre.backend.entity.Usuario;
import com.sementelivre.backend.entity.enums.Disponibilidade;
import com.sementelivre.backend.entity.enums.OrigemMovimentacao;
import com.sementelivre.backend.entity.enums.StatusPedido;
import com.sementelivre.backend.entity.enums.TipoMovimentacao;
import com.sementelivre.backend.entity.enums.TipoPedido;
import com.sementelivre.backend.repository.EstoqueRepository;
import com.sementelivre.backend.repository.PedidoRepository;
import com.sementelivre.backend.repository.ProdutoRepository;
import com.sementelivre.backend.exception.EstoqueInsuficienteException;
import com.sementelivre.backend.exception.NegociacaoNaoPermitidaException;
import com.sementelivre.backend.exception.RecursoNaoEncontradoException;
import com.sementelivre.backend.exception.TransicaoStatusInvalidaException;
import com.sementelivre.backend.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class PedidoServiceTest {

    @Mock
    private PedidoRepository pedidoRepository;

    @Mock
    private ProdutoRepository produtoRepository;

    @Mock
    private EstoqueRepository estoqueRepository;

    @Mock
    private EstoqueService estoqueService;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private NotificacaoService notificacaoService;

    @Mock
    private ApplicationEventPublisher eventos;

    private PedidoService pedidoService;

    private UUID pedidoId;
    private UUID produtoId;
    private UUID proprietarioId;
    private UUID usuarioId;

    private Produto produto;
    private Proprietario proprietario;
    private Usuario usuario;
    private Estoque estoque;

    private static final double ESTOQUE_INICIAL = 10.0;
    private static final double QUANTIDADE_PEDIDA = 4.0;

    @BeforeEach
    void setUp() {
        pedidoService = new PedidoService(
                pedidoRepository,
                produtoRepository,
                estoqueRepository,
                estoqueService,
                usuarioRepository,
                notificacaoService,
                eventos
        );

        pedidoId = UUID.randomUUID();
        produtoId = UUID.randomUUID();
        proprietarioId = UUID.randomUUID();
        usuarioId = UUID.randomUUID();

        produto = Produto.builder()
                .id(produtoId)
                .nomePopular("Milho crioulo")
                .build();

        proprietario = new Proprietario();
        proprietario.setId(proprietarioId);

        Proprietario solicitante = new Proprietario();
        solicitante.setId(usuarioId);
        solicitante.setNome("João");
        solicitante.setEmail("joao@exemplo.com");

        usuario = new Usuario();
        usuario.setId(usuarioId);
        usuario.setPessoa(solicitante);

        estoque = Estoque.builder()
                .id(UUID.randomUUID())
                .produto(produto)
                .proprietario(proprietario)
                .quantidade(ESTOQUE_INICIAL)
                .preco(15.0)
                .disponibilidade(Disponibilidade.PARA_VENDA)
                .build();
    }

    // CRIAR

    @Test
    void deveCriarPedidoPendenteNaFilaSemMexerNoEstoque() {
        mockUsuarioEProdutoExistentes();
        mockEstoqueDisponivel();
        mockSalvarPedido();

        PedidoResponseDTO resposta = pedidoService.criar(requestComQuantidade(QUANTIDADE_PEDIDA));

        assertNotNull(resposta);
        assertEquals(StatusPedido.PENDENTE, resposta.status());
        assertEquals(1, resposta.itens().size());
        assertEquals(produtoId, resposta.itens().get(0).produtoId());

        // Fila (#183): o estoque so baixa quando o produtor confirma
        assertEquals(ESTOQUE_INICIAL, estoque.getQuantidade());
        verify(estoqueRepository, never()).save(any(Estoque.class));
        verify(estoqueRepository, never()).findParaAtualizacao(any(UUID.class), any(UUID.class));
        verify(notificacaoService).criarParaPedidoRegistrado(any(Pedido.class));
    }

    @Test
    void pedidosNaFilaPodemSomarMaisQueOEstoque() {
        mockUsuarioEProdutoExistentes();
        mockEstoqueDisponivel();
        mockSalvarPedido();

        // Dois pedidos de 8 com 10 em estoque: os dois entram, o produtor escolhe
        pedidoService.criar(requestComQuantidade(8.0));
        pedidoService.criar(requestComQuantidade(8.0));

        assertEquals(ESTOQUE_INICIAL, estoque.getQuantidade());
    }

    @Test
    void deveCriarPedidoComCompradorERetornarNomeDoProduto() {
        mockUsuarioEProdutoExistentes();
        mockEstoqueDisponivel();
        mockSalvarPedido();

        PedidoRequestDTO dto = new PedidoRequestDTO(
                TipoPedido.DOACAO,
                null,
                usuarioId,
                proprietarioId,
                List.of(new ItemPedidoRequestDTO(produtoId, QUANTIDADE_PEDIDA, null)),
                new CompradorDTO("  Maria Silva ", "32999990000")
        );

        PedidoResponseDTO resposta = pedidoService.criar(dto);

        assertEquals("Maria Silva", resposta.comprador().nome());
        assertEquals("32999990000", resposta.comprador().telefone());
        assertEquals("Milho crioulo", resposta.itens().get(0).nomeProduto());
    }

    @Test
    void deveCriarPedidoSemComprador() {
        mockUsuarioEProdutoExistentes();
        mockEstoqueDisponivel();
        mockSalvarPedido();

        PedidoResponseDTO resposta = pedidoService.criar(requestComQuantidade(QUANTIDADE_PEDIDA));

        assertNull(resposta.comprador());
    }

    @Test
    void devePublicarCopiaDoPedidoParaOEmailDoSolicitante() {
        mockUsuarioEProdutoExistentes();
        mockEstoqueDisponivel();
        mockSalvarPedido();

        pedidoService.criar(requestComQuantidade(QUANTIDADE_PEDIDA));

        assertEquals("joao@exemplo.com", copiaPublicada().para());
    }

    // CRIAR PELO SITE PÚBLICO

    @Test
    void deveCriarPedidoPublicoComDadosDaOfertaESemSolicitante() {
        when(estoqueRepository.findById(estoque.getId())).thenReturn(Optional.of(estoque));
        mockEstoqueDisponivel();
        mockSalvarPedido();

        PedidoPublicoResponseDTO resposta = pedidoService.criarPublico(requestPublico());

        ArgumentCaptor<Pedido> salvo = ArgumentCaptor.forClass(Pedido.class);
        verify(pedidoRepository).save(salvo.capture());
        Pedido pedido = salvo.getValue();

        assertNull(pedido.getUsuarioSolicitante());
        assertEquals(proprietario, pedido.getProprietarioRecebedor());
        // PARA_VENDA vira VENDA; preço vem do estoque, não do navegador
        assertEquals(TipoPedido.VENDA, pedido.getTipoPedido());
        assertEquals(15.0, pedido.getItens().get(0).getPrecoUnitario());
        assertEquals("Ana Souza", pedido.getComprador().getNome());
        assertEquals("ana@exemplo.com", pedido.getComprador().getEmail());
        assertNull(pedido.getComprador().getTelefone());
        assertNull(pedido.getMensagemOpcional());

        assertEquals(StatusPedido.PENDENTE, resposta.status());
        assertEquals("Milho crioulo", resposta.produto());
        assertEquals(ESTOQUE_INICIAL, estoque.getQuantidade());
        verify(notificacaoService).criarParaPedidoRegistrado(pedido);
    }

    @Test
    void devePublicarCopiaDoPedidoPublicoParaOEmailInformado() {
        when(estoqueRepository.findById(estoque.getId())).thenReturn(Optional.of(estoque));
        mockEstoqueDisponivel();
        mockSalvarPedido();

        pedidoService.criarPublico(requestPublico());

        CopiaDoPedidoEmail copia = copiaPublicada();
        assertEquals("ana@exemplo.com", copia.para());
        assertTrue(copia.texto().contains("Olá, Ana Souza!"), copia.texto());
        assertTrue(copia.texto().contains("4 x Milho crioulo"), copia.texto());
    }

    // NEGOCIAÇÃO DO TIPO NO PEDIDO PÚBLICO

    @Test
    void ofertaQueAceitaNegociarDeixaProporOutroTipoSemPreco() {
        when(estoqueRepository.findById(estoque.getId())).thenReturn(Optional.of(estoque));
        mockEstoqueDisponivel();
        mockSalvarPedido();

        PedidoPublicoResponseDTO resposta = pedidoService.criarPublico(requestPublicoComTipo(TipoPedido.TROCA));

        Pedido pedido = pedidoSalvo();
        assertEquals(TipoPedido.TROCA, resposta.tipoPedido());
        assertEquals(TipoPedido.TROCA, pedido.getTipoPedido());
        // o preço da oferta é de venda: numa troca proposta não há total
        assertNull(pedido.getItens().get(0).getPrecoUnitario());
    }

    @Test
    void ofertaQueNaoAceitaNegociarRecusaOutroTipo() {
        estoque.setAceitaNegociacao(false);
        when(estoqueRepository.findById(estoque.getId())).thenReturn(Optional.of(estoque));

        NegociacaoNaoPermitidaException erro = assertThrows(NegociacaoNaoPermitidaException.class,
                () -> pedidoService.criarPublico(requestPublicoComTipo(TipoPedido.DOACAO)));

        assertEquals("O produtor não aceita negociar esta oferta: o pedido precisa ser de venda.", erro.getMessage());
        assertEquals(ESTOQUE_INICIAL, estoque.getQuantidade());
        verify(pedidoRepository, never()).save(any(Pedido.class));
    }

    @Test
    void ofertaQueNaoAceitaNegociarAceitaOProprioTipo() {
        estoque.setAceitaNegociacao(false);
        when(estoqueRepository.findById(estoque.getId())).thenReturn(Optional.of(estoque));
        mockEstoqueDisponivel();
        mockSalvarPedido();

        PedidoPublicoResponseDTO resposta = pedidoService.criarPublico(requestPublicoComTipo(TipoPedido.VENDA));

        assertEquals(TipoPedido.VENDA, resposta.tipoPedido());
        assertEquals(15.0, pedidoSalvo().getItens().get(0).getPrecoUnitario());
    }

    @Test
    void semTipoInformadoValeODaOfertaMesmoSemNegociacao() {
        estoque.setAceitaNegociacao(false);
        estoque.setDisponibilidade(Disponibilidade.PARA_DOACAO);
        when(estoqueRepository.findById(estoque.getId())).thenReturn(Optional.of(estoque));
        mockEstoqueDisponivel();
        mockSalvarPedido();

        PedidoPublicoResponseDTO resposta = pedidoService.criarPublico(requestPublico());

        assertEquals(TipoPedido.DOACAO, resposta.tipoPedido());
        assertNull(pedidoSalvo().getItens().get(0).getPrecoUnitario());
    }

    @Test
    void naoDeveCriarPedidoPublicoParaOfertaForaDoCatalogo() {
        estoque.setDisponibilidade(Disponibilidade.A_NEGOCIAR);
        when(estoqueRepository.findById(estoque.getId())).thenReturn(Optional.of(estoque));

        assertThrows(RecursoNaoEncontradoException.class,
                () -> pedidoService.criarPublico(requestPublico()));

        verify(pedidoRepository, never()).save(any(Pedido.class));
        verify(eventos, never()).publishEvent(any(Object.class));
    }

    @Test
    void naoDeveCriarPedidoPublicoParaOfertaInexistente() {
        when(estoqueRepository.findById(estoque.getId())).thenReturn(Optional.empty());

        assertThrows(RecursoNaoEncontradoException.class,
                () -> pedidoService.criarPublico(requestPublico()));
    }

    @Test
    void naoDeveCriarPedidoPublicoQuandoQuantidadeMaiorQueEstoque() {
        when(estoqueRepository.findById(estoque.getId())).thenReturn(Optional.of(estoque));
        mockEstoqueDisponivel();

        PedidoPublicoRequestDTO dto = new PedidoPublicoRequestDTO(
                estoque.getId(), ESTOQUE_INICIAL + 1, "Ana Souza", "ana@exemplo.com", null, null);

        assertThrows(EstoqueInsuficienteException.class, () -> pedidoService.criarPublico(dto));

        verify(pedidoRepository, never()).save(any(Pedido.class));
        verify(eventos, never()).publishEvent(any(Object.class));
    }

    @Test
    void naoDeveCriarPedidoQuandoQuantidadeMaiorQueEstoque() {
        mockUsuarioEProdutoExistentes();
        mockEstoqueDisponivel();

        EstoqueInsuficienteException erro = assertThrows(
                EstoqueInsuficienteException.class,
                () -> pedidoService.criar(requestComQuantidade(ESTOQUE_INICIAL + 1))
        );

        assertEquals("Estoque insuficiente para o produto Milho crioulo: disponível 10, solicitado 11.",
                erro.getMessage());
        verify(pedidoRepository, never()).save(any(Pedido.class));
        verify(notificacaoService, never()).criarParaPedidoRegistrado(any(Pedido.class));
    }

    @Test
    void naoDeveCriarPedidoQuandoProprietarioNaoTemEstoqueDoProduto() {
        mockUsuarioEProdutoExistentes();

        when(estoqueRepository.findByProprietarioIdAndProdutoId(proprietarioId, produtoId))
                .thenReturn(Optional.empty());

        EstoqueInsuficienteException erro = assertThrows(
                EstoqueInsuficienteException.class,
                () -> pedidoService.criar(requestComQuantidade(QUANTIDADE_PEDIDA))
        );

        assertEquals("O produto Milho crioulo não está disponível no estoque deste proprietário.",
                erro.getMessage());
        verify(pedidoRepository, never()).save(any(Pedido.class));
    }

    @Test
    void naoDeveCriarPedidoQuandoEstoqueEstaIndisponivel() {
        estoque.setDisponibilidade(Disponibilidade.INDISPONIVEL);
        mockUsuarioEProdutoExistentes();
        mockEstoqueDisponivel();

        EstoqueInsuficienteException erro = assertThrows(
                EstoqueInsuficienteException.class,
                () -> pedidoService.criar(requestComQuantidade(QUANTIDADE_PEDIDA))
        );

        assertEquals("O produto Milho crioulo está marcado como indisponível no estoque deste proprietário.",
                erro.getMessage());
        assertEquals(ESTOQUE_INICIAL, estoque.getQuantidade());
        verify(estoqueRepository, never()).save(any(Estoque.class));
        verify(pedidoRepository, never()).save(any(Pedido.class));
    }

    @Test
    void deveConsolidarProdutoRepetidoEmUmUnicoItem() {
        mockUsuarioEProdutoExistentes();
        mockEstoqueDisponivel();
        mockSalvarPedido();

        PedidoRequestDTO dto = new PedidoRequestDTO(
                TipoPedido.VENDA,
                null,
                usuarioId,
                proprietarioId,
                List.of(
                        new ItemPedidoRequestDTO(produtoId, 3.0, null),
                        new ItemPedidoRequestDTO(produtoId, 2.0, 15.0)
                ),
                null
        );

        PedidoResponseDTO resposta = pedidoService.criar(dto);

        assertEquals(1, resposta.itens().size());
        assertEquals(5.0, resposta.itens().get(0).quantidade());
        assertEquals(15.0, resposta.itens().get(0).precoUnitario());
        assertEquals(ESTOQUE_INICIAL, estoque.getQuantidade());
    }

    @Test
    void naoDeveCriarPedidoComMesmoProdutoEPrecosDiferentes() {
        mockUsuarioEProdutoExistentes();

        PedidoRequestDTO dto = new PedidoRequestDTO(
                TipoPedido.VENDA,
                null,
                usuarioId,
                proprietarioId,
                List.of(
                        new ItemPedidoRequestDTO(produtoId, 3.0, 15.0),
                        new ItemPedidoRequestDTO(produtoId, 2.0, 12.0)
                ),
                null
        );

        assertThrows(IllegalArgumentException.class, () -> pedidoService.criar(dto));

        verify(estoqueRepository, never()).save(any(Estoque.class));
        verify(pedidoRepository, never()).save(any(Pedido.class));
    }

    @Test
    void deveSomarQuantidadesDoMesmoProdutoAoValidarEstoque() {
        mockUsuarioEProdutoExistentes();
        mockEstoqueDisponivel();

        // 6 + 5 = 11 estoura os 10 em estoque, embora nenhum item sozinho estoure
        PedidoRequestDTO dto = new PedidoRequestDTO(
                TipoPedido.VENDA,
                "Dois itens do mesmo produto",
                usuarioId,
                proprietarioId,
                List.of(
                        new ItemPedidoRequestDTO(produtoId, 6.0, 15.0),
                        new ItemPedidoRequestDTO(produtoId, 5.0, 15.0)
                ),
                null
        );

        assertThrows(
                EstoqueInsuficienteException.class,
                () -> pedidoService.criar(dto)
        );

        verify(pedidoRepository, never()).save(any(Pedido.class));
    }

    @Test
    void naoDeveCriarPedidoQuandoProdutoNaoExiste() {
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(usuario));
        when(produtoRepository.findById(produtoId)).thenReturn(Optional.empty());

        assertThrows(
                RecursoNaoEncontradoException.class,
                () -> pedidoService.criar(requestComQuantidade(QUANTIDADE_PEDIDA))
        );

        verify(pedidoRepository, never()).save(any(Pedido.class));
    }

    @Test
    void naoDeveCriarPedidoQuandoUsuarioSolicitanteNaoExiste() {
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.empty());

        assertThrows(
                RecursoNaoEncontradoException.class,
                () -> pedidoService.criar(requestComQuantidade(QUANTIDADE_PEDIDA))
        );

        verify(produtoRepository, never()).findById(any(UUID.class));
        verify(pedidoRepository, never()).save(any(Pedido.class));
    }

    // CONFIRMAR

    @Test
    void deveConfirmarPedidoEBaixarEstoque() {
        mockConfirmacao();
        mockBuscaPedido(pedidoPendente());
        mockSalvarPedido();

        PedidoResponseDTO resposta = pedidoService.confirmar(pedidoId);

        assertEquals(StatusPedido.CONFIRMADO, resposta.status());
        assertEquals(ESTOQUE_INICIAL - QUANTIDADE_PEDIDA, estoque.getQuantidade());
        verify(estoqueRepository).save(estoque);
        verify(notificacaoService).criarParaPedidoConfirmado(any(Pedido.class));
    }

    @Test
    void confirmacaoViraSaidaNoHistoricoDoEstoque() {
        mockConfirmacao();
        mockBuscaPedido(pedidoPendente());
        mockSalvarPedido();

        pedidoService.confirmar(pedidoId);

        // Sem este lançamento o histórico continuava no saldo antigo (#185)
        verify(estoqueService).registrarLancamentoDePedido(estoque, TipoMovimentacao.SAIDA_VENDA,
                OrigemMovimentacao.PEDIDO, ESTOQUE_INICIAL, "Pedido de João");
    }

    @Test
    void saidaNoHistoricoSegueOTipoDoPedido() {
        Pedido doacao = pedidoPendente();
        doacao.setTipoPedido(TipoPedido.DOACAO);
        doacao.setComprador(Comprador.builder().nome("Maria Silva").build());
        mockConfirmacao();
        mockBuscaPedido(doacao);
        mockSalvarPedido();

        pedidoService.confirmar(pedidoId);

        verify(estoqueService).registrarLancamentoDePedido(estoque, TipoMovimentacao.SAIDA_DOACAO,
                OrigemMovimentacao.PEDIDO, ESTOQUE_INICIAL, "Pedido de Maria Silva");
    }

    @Test
    void naoDeveConfirmarQuandoOEstoqueNaoCobreMais() {
        // O produtor tirou do estoque depois que o pedido entrou na fila
        estoque.setQuantidade(3.0);
        Pedido pedido = pedidoPendente();
        mockConfirmacao();
        mockBuscaPedido(pedido);

        EstoqueInsuficienteException erro = assertThrows(EstoqueInsuficienteException.class,
                () -> pedidoService.confirmar(pedidoId));

        assertEquals("Estoque insuficiente para o produto Milho crioulo: disponível 3, solicitado 4.",
                erro.getMessage());
        assertEquals(StatusPedido.PENDENTE, pedido.getStatus());
        assertEquals(3.0, estoque.getQuantidade());
        verify(pedidoRepository, never()).save(any(Pedido.class));
    }

    @Test
    void confirmacaoTravaOEstoqueAntesDoPedido() {
        mockConfirmacao();
        mockBuscaPedido(pedidoPendente());
        mockSalvarPedido();

        pedidoService.confirmar(pedidoId);

        // Duas confirmacoes do mesmo produto esperam no estoque, sem deadlock
        org.mockito.InOrder ordem = org.mockito.Mockito.inOrder(estoqueRepository, pedidoRepository);
        ordem.verify(estoqueRepository).findParaAtualizacao(proprietarioId, produtoId);
        ordem.verify(pedidoRepository).findParaAtualizacao(pedidoId);
    }

    @Test
    void confirmarRecusaQuemNaoCabeMaisNoEstoque() {
        // 50 kg; pedidos de 49 e 10. Confirmado o de 49, sobra 1 e o de 10 sai da fila.
        estoque.setQuantidade(50.0);
        Pedido grande = pedidoPendenteDe(pedidoId, 49.0);
        UUID pequenoId = UUID.randomUUID();
        Pedido pequeno = pedidoPendenteDe(pequenoId, 10.0);
        pequeno.setComprador(Comprador.builder().nome("Bia").email("bia@exemplo.com").build());

        mockConfirmacao();
        mockBuscaPedido(grande);
        when(pedidoRepository.findParaAtualizacao(pequenoId)).thenReturn(Optional.of(pequeno));
        when(pedidoRepository.findIdsNaFila(eq(proprietarioId), any(), eq(pedidoId))).thenReturn(List.of(pequenoId));
        mockSalvarPedido();

        pedidoService.confirmar(pedidoId);

        assertEquals(1.0, estoque.getQuantidade());
        assertEquals(StatusPedido.CONFIRMADO, grande.getStatus());
        assertEquals(StatusPedido.CANCELADO, pequeno.getStatus());
        verify(notificacaoService).criarParaPedidoRecusado(pequeno);

        CopiaDoPedidoEmail aviso = copiaPublicada();
        assertEquals("bia@exemplo.com", aviso.para());
        assertEquals("Semente Livre - Seu pedido foi recusado", aviso.assunto());
        assertTrue(aviso.texto().contains("10 x Milho crioulo (disponível agora: 1)"), aviso.texto());
    }

    @Test
    void confirmarMantemNaFilaQuemAindaCabe() {
        // 50 kg; pedidos de 40 e 10. Confirmado o de 40, sobram 10: o de 10 continua na fila.
        estoque.setQuantidade(50.0);
        Pedido grande = pedidoPendenteDe(pedidoId, 40.0);
        UUID pequenoId = UUID.randomUUID();
        Pedido pequeno = pedidoPendenteDe(pequenoId, 10.0);

        mockConfirmacao();
        mockBuscaPedido(grande);
        when(pedidoRepository.findParaAtualizacao(pequenoId)).thenReturn(Optional.of(pequeno));
        when(pedidoRepository.findIdsNaFila(eq(proprietarioId), any(), eq(pedidoId))).thenReturn(List.of(pequenoId));
        mockSalvarPedido();

        pedidoService.confirmar(pedidoId);

        assertEquals(10.0, estoque.getQuantidade());
        assertEquals(StatusPedido.PENDENTE, pequeno.getStatus());
        verify(notificacaoService, never()).criarParaPedidoRecusado(any(Pedido.class));
        verify(eventos, never()).publishEvent(any(Object.class));
    }

    @Test
    void recusaIgnoraPedidoQueJaSaiuDaFila() {
        // Cancelado por outra requisicao entre a consulta da fila e o lock
        estoque.setQuantidade(50.0);
        Pedido grande = pedidoPendenteDe(pedidoId, 49.0);
        UUID outroId = UUID.randomUUID();
        Pedido jaCancelado = pedidoPendenteDe(outroId, 10.0);
        jaCancelado.setStatus(StatusPedido.CANCELADO);

        mockConfirmacao();
        mockBuscaPedido(grande);
        when(pedidoRepository.findParaAtualizacao(outroId)).thenReturn(Optional.of(jaCancelado));
        when(pedidoRepository.findIdsNaFila(eq(proprietarioId), any(), eq(pedidoId))).thenReturn(List.of(outroId));
        mockSalvarPedido();

        pedidoService.confirmar(pedidoId);

        verify(notificacaoService, never()).criarParaPedidoRecusado(any(Pedido.class));
    }

    @Test
    void naoDeveConfirmarPedidoJaConfirmado() {
        mockConfirmacao();
        mockBuscaPedido(pedidoComStatus(StatusPedido.CONFIRMADO));

        assertThrows(
                TransicaoStatusInvalidaException.class,
                () -> pedidoService.confirmar(pedidoId)
        );

        // Falha antes de baixar: nada de baixa dupla
        verify(estoqueRepository, never()).save(any(Estoque.class));
        assertEquals(ESTOQUE_INICIAL, estoque.getQuantidade());
    }

    @Test
    void naoDeveConfirmarPedidoCancelado() {
        mockConfirmacao();
        mockBuscaPedido(pedidoComStatus(StatusPedido.CANCELADO));

        assertThrows(
                TransicaoStatusInvalidaException.class,
                () -> pedidoService.confirmar(pedidoId)
        );

        verify(estoqueRepository, never()).save(any(Estoque.class));
    }

    // CANCELAR

    @Test
    void deveCancelarPedidoConfirmadoERestaurarEstoque() {
        // Estoque ja baixado pela confirmacao anterior
        estoque.setQuantidade(ESTOQUE_INICIAL - QUANTIDADE_PEDIDA);

        mockBuscaPedido(pedidoComStatus(StatusPedido.CONFIRMADO));
        mockEstoqueParaAtualizacao();
        mockSalvarPedido();

        PedidoResponseDTO resposta = pedidoService.cancelar(pedidoId);

        assertEquals(StatusPedido.CANCELADO, resposta.status());
        assertEquals(ESTOQUE_INICIAL, estoque.getQuantidade());

        verify(estoqueRepository).save(estoque);
        verify(notificacaoService).criarParaPedidoCancelado(any(Pedido.class), eq(true));
        verify(estoqueService).registrarLancamentoDePedido(estoque, TipoMovimentacao.ENTRADA,
                OrigemMovimentacao.DEVOLUCAO, ESTOQUE_INICIAL - QUANTIDADE_PEDIDA, "Devolução do pedido de João");
    }

    @Test
    void deveCancelarPedidoPendenteSemMexerNoEstoque() {
        mockBuscaPedido(pedidoPendente());
        mockSalvarPedido();

        PedidoResponseDTO resposta = pedidoService.cancelar(pedidoId);

        // Ainda estava na fila: nada foi baixado, nada a devolver
        assertEquals(StatusPedido.CANCELADO, resposta.status());
        assertEquals(ESTOQUE_INICIAL, estoque.getQuantidade());
        verify(estoqueRepository, never()).save(any(Estoque.class));
        verify(notificacaoService).criarParaPedidoCancelado(any(Pedido.class), eq(false));
    }

    @Test
    void naoDeveCancelarPedidoJaCancelado() {
        mockBuscaPedido(pedidoComStatus(StatusPedido.CANCELADO));

        assertThrows(
                TransicaoStatusInvalidaException.class,
                () -> pedidoService.cancelar(pedidoId)
        );

        // Cancelar duas vezes devolveria o estoque em dobro
        verify(estoqueRepository, never()).save(any(Estoque.class));
        verify(estoqueService, never()).registrarLancamentoDePedido(
                any(Estoque.class), any(TipoMovimentacao.class), any(OrigemMovimentacao.class), anyDouble(), anyString());
        verify(notificacaoService, never()).criarParaPedidoCancelado(any(Pedido.class), anyBoolean());
    }

    @Test
    void deveCancelarPedidoBuscandoComLock() {
        mockBuscaPedido(pedidoPendente());
        mockSalvarPedido();

        pedidoService.cancelar(pedidoId);

        // A busca sem lock deixaria dois cancelamentos simultaneos restaurarem em dobro
        verify(pedidoRepository).findParaAtualizacao(pedidoId);
        verify(pedidoRepository, never()).findByIdComItens(any(UUID.class));
    }

    @Test
    void deveExcluirPedidoCanceladoSemRestaurarEstoqueNovamente() {
        Pedido pedido = pedidoComStatus(StatusPedido.CANCELADO);
        mockBuscaPedido(pedido);

        pedidoService.excluir(pedidoId);

        verify(estoqueRepository, never()).findParaAtualizacao(any(UUID.class), any(UUID.class));
        verify(estoqueRepository, never()).save(any(Estoque.class));
        verify(pedidoRepository).delete(pedido);
    }

    @Test
    void deveLancarExcecaoAoCancelarPedidoInexistente() {
        when(pedidoRepository.findParaAtualizacao(pedidoId)).thenReturn(Optional.empty());

        assertThrows(
                RecursoNaoEncontradoException.class,
                () -> pedidoService.cancelar(pedidoId)
        );
    }

    // EXCLUIR (CDU-14)

    @Test
    void deveRestaurarEstoqueAoExcluirPedidoConfirmado() {
        estoque.setQuantidade(ESTOQUE_INICIAL - QUANTIDADE_PEDIDA);

        Pedido pedido = pedidoComStatus(StatusPedido.CONFIRMADO);
        mockBuscaPedido(pedido);
        mockEstoqueParaAtualizacao();

        pedidoService.excluir(pedidoId);

        assertEquals(ESTOQUE_INICIAL, estoque.getQuantidade());
        verify(estoqueRepository).save(estoque);
        verify(pedidoRepository).delete(pedido);
        verify(estoqueService).registrarLancamentoDePedido(eq(estoque), eq(TipoMovimentacao.ENTRADA),
                eq(OrigemMovimentacao.DEVOLUCAO), eq(ESTOQUE_INICIAL - QUANTIDADE_PEDIDA), anyString());
    }

    @Test
    void deveExcluirPedidoPendenteSemMexerNoEstoque() {
        Pedido pedido = pedidoPendente();
        mockBuscaPedido(pedido);

        pedidoService.excluir(pedidoId);

        assertEquals(ESTOQUE_INICIAL, estoque.getQuantidade());
        verify(estoqueRepository, never()).save(any(Estoque.class));
        verify(pedidoRepository).delete(pedido);
    }

    // ATUALIZAR (CDU-13)

    @Test
    void deveAtualizarPedidoPendente() {
        mockBuscaPedido(pedidoPendente());
        when(produtoRepository.findById(produtoId)).thenReturn(Optional.of(produto));
        mockEstoqueDisponivel();
        mockSalvarPedido();

        PedidoUpdateDTO dto = new PedidoUpdateDTO(
                TipoPedido.TROCA,
                "Mensagem alterada",
                List.of(new ItemPedidoRequestDTO(produtoId, 2.0, 15.0))
        );

        PedidoResponseDTO resposta = pedidoService.atualizar(pedidoId, dto);

        assertEquals(TipoPedido.TROCA, resposta.tipoPedido());
        assertEquals("Mensagem alterada", resposta.mensagemOpcional());
        assertEquals(1, resposta.itens().size());
        assertEquals(2.0, resposta.itens().get(0).quantidade());
    }

    @Test
    void naoDeveAtualizarPedidoConfirmado() {
        mockBuscaPedido(pedidoComStatus(StatusPedido.CONFIRMADO));

        PedidoUpdateDTO dto = new PedidoUpdateDTO(
                TipoPedido.TROCA,
                "Tentativa de alteração",
                List.of(new ItemPedidoRequestDTO(produtoId, 2.0, 15.0))
        );

        assertThrows(
                TransicaoStatusInvalidaException.class,
                () -> pedidoService.atualizar(pedidoId, dto)
        );

        verify(pedidoRepository, never()).save(any(Pedido.class));
    }

    @Test
    void naoDeveAtualizarQuandoNovaQuantidadeExcedeEstoque() {
        mockBuscaPedido(pedidoPendente());
        when(produtoRepository.findById(produtoId)).thenReturn(Optional.of(produto));
        mockEstoqueDisponivel();

        PedidoUpdateDTO dto = new PedidoUpdateDTO(
                TipoPedido.VENDA,
                "Quantidade acima do estoque",
                List.of(new ItemPedidoRequestDTO(produtoId, ESTOQUE_INICIAL + 5, 15.0))
        );

        assertThrows(
                EstoqueInsuficienteException.class,
                () -> pedidoService.atualizar(pedidoId, dto)
        );

        verify(pedidoRepository, never()).save(any(Pedido.class));
    }

    // CONSULTA

    @Test
    void deveListarPedidosComItens() {
        UUID proprietarioId = java.util.UUID.randomUUID();
        when(pedidoRepository.findAllByProprietarioRecebedorId(proprietarioId)).thenReturn(List.of(pedidoPendente()));

        List<PedidoResponseDTO> resultado = pedidoService.listarTodos(proprietarioId);

        assertEquals(1, resultado.size());
        assertEquals(pedidoId, resultado.get(0).id());
        assertEquals(1, resultado.get(0).itens().size());
    }

    @Test
    void deveFiltrarHistoricoPorPeriodoTipoStatusEProduto() {
        Pedido antigo = pedidoComStatus(StatusPedido.CONFIRMADO);
        antigo.setId(UUID.randomUUID());
        antigo.setDataPedido(LocalDateTime.of(2026, 8, 10, 9, 0));

        Pedido recente = pedidoComStatus(StatusPedido.PENDENTE);
        recente.setId(UUID.randomUUID());
        recente.setTipoPedido(TipoPedido.TROCA);
        recente.setDataPedido(LocalDateTime.of(2026, 9, 20, 23, 59));

        when(pedidoRepository.findAllByProprietarioRecebedorId(proprietarioId))
                .thenReturn(List.of(antigo, recente));

        // sem filtro: os dois, mais recente primeiro
        List<PedidoResponseDTO> todos = pedidoService.listarTodos(proprietarioId, PedidoFiltroDTO.vazio());
        assertEquals(List.of(recente.getId(), antigo.getId()), todos.stream().map(PedidoResponseDTO::id).toList());

        // periodo inclusivo: o pedido das 23:59 do ultimo dia entra
        assertEquals(List.of(recente.getId()), ids(new PedidoFiltroDTO(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 20), null, null, null)));

        assertEquals(List.of(recente.getId()), ids(new PedidoFiltroDTO(
                null, null, TipoPedido.TROCA, null, null)));

        assertEquals(List.of(antigo.getId()), ids(new PedidoFiltroDTO(
                null, null, null, null, StatusPedido.CONFIRMADO)));

        assertEquals(2, ids(new PedidoFiltroDTO(null, null, null, produtoId, null)).size());
        assertEquals(0, ids(new PedidoFiltroDTO(null, null, null, UUID.randomUUID(), null)).size());
    }

    @Test
    void deveLancarExcecaoAoBuscarPedidoInexistente() {
        when(pedidoRepository.findByIdComItens(pedidoId)).thenReturn(Optional.empty());

        assertThrows(
                RecursoNaoEncontradoException.class,
                () -> pedidoService.buscarPorId(pedidoId)
        );
    }

    // AUXILIARES

    private List<UUID> ids(PedidoFiltroDTO filtro) {
        return pedidoService.listarTodos(proprietarioId, filtro).stream()
                .map(PedidoResponseDTO::id)
                .toList();
    }

    private PedidoRequestDTO requestComQuantidade(double quantidade) {
        return new PedidoRequestDTO(
                TipoPedido.VENDA,
                "Pedido de teste",
                usuarioId,
                proprietarioId,
                List.of(new ItemPedidoRequestDTO(produtoId, quantidade, 15.0)),
                null
        );
    }

    private PedidoPublicoRequestDTO requestPublicoComTipo(TipoPedido tipo) {
        return new PedidoPublicoRequestDTO(
                estoque.getId(), QUANTIDADE_PEDIDA, "Ana Souza", "ana@exemplo.com", null, null, tipo);
    }

    private Pedido pedidoSalvo() {
        ArgumentCaptor<Pedido> salvo = ArgumentCaptor.forClass(Pedido.class);
        verify(pedidoRepository).save(salvo.capture());
        return salvo.getValue();
    }

    private PedidoPublicoRequestDTO requestPublico() {
        return new PedidoPublicoRequestDTO(
                estoque.getId(), QUANTIDADE_PEDIDA, " Ana Souza ", "ana@exemplo.com", "  ", "");
    }

    private CopiaDoPedidoEmail copiaPublicada() {
        ArgumentCaptor<Object> evento = ArgumentCaptor.forClass(Object.class);
        verify(eventos).publishEvent(evento.capture());
        return (CopiaDoPedidoEmail) evento.getValue();
    }

    private Pedido pedidoPendente() {
        return pedidoComStatus(StatusPedido.PENDENTE);
    }

    private Pedido pedidoPendenteDe(UUID id, double quantidade) {
        Pedido pedido = pedidoPendente();
        pedido.setId(id);
        pedido.getItens().get(0).setQuantidade(quantidade);
        pedido.prePersist();
        return pedido;
    }

    // Confirmacao trava o estoque antes do pedido, a partir de consultas escalares
    private void mockConfirmacao() {
        lenient().when(pedidoRepository.findProprietarioRecebedorId(pedidoId)).thenReturn(Optional.of(proprietarioId));
        lenient().when(pedidoRepository.findProdutoIdsDoPedido(pedidoId)).thenReturn(List.of(produtoId));
        lenient().when(estoqueRepository.findParaAtualizacao(proprietarioId, produtoId)).thenReturn(Optional.of(estoque));
    }

    private Pedido pedidoComStatus(StatusPedido status) {
        Pedido pedido = Pedido.builder()
                .id(pedidoId)
                .tipoPedido(TipoPedido.VENDA)
                .mensagemOpcional("Pedido de teste")
                .status(status)
                .usuarioSolicitante(usuario)
                .proprietarioRecebedor(proprietario)
                .build();

        pedido.adicionarItem(
                Itens.builder()
                        .id(UUID.randomUUID())
                        .produto(produto)
                        .quantidade(QUANTIDADE_PEDIDA)
                        .precoUnitario(15.0)
                        .build()
        );

        return pedido;
    }

    private void mockUsuarioEProdutoExistentes() {
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(usuario));
        when(produtoRepository.findById(produtoId)).thenReturn(Optional.of(produto));
    }

    private void mockEstoqueDisponivel() {
        when(estoqueRepository.findByProprietarioIdAndProdutoId(proprietarioId, produtoId))
                .thenReturn(Optional.of(estoque));
    }

    private void mockEstoqueParaAtualizacao() {
        when(estoqueRepository.findParaAtualizacao(proprietarioId, produtoId))
                .thenReturn(Optional.of(estoque));
    }

    // Operacoes que mudam o pedido buscam com lock pessimista
    private void mockBuscaPedido(Pedido pedido) {
        when(pedidoRepository.findParaAtualizacao(pedidoId)).thenReturn(Optional.of(pedido));
    }

    private void mockSalvarPedido() {
        when(pedidoRepository.save(any(Pedido.class)))
                .thenAnswer(invocation -> {
                    Pedido salvo = invocation.getArgument(0);
                    if (salvo.getId() == null) {
                        salvo.setId(pedidoId);
                    }
                    // O JPA roda o @PrePersist no save; o mock não
                    if (salvo.getDataPedido() == null) {
                        salvo.prePersist();
                    }
                    return salvo;
                });
    }
}
