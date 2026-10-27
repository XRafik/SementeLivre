package com.sementelivre.backend.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sementelivre.backend.dto.EstoqueRequestDTO;
import com.sementelivre.backend.dto.EstoqueResponseDTO;
import com.sementelivre.backend.entity.Estoque;
import com.sementelivre.backend.entity.Produto;
import com.sementelivre.backend.entity.Proprietario;
import com.sementelivre.backend.entity.enums.Disponibilidade;
import com.sementelivre.backend.entity.enums.Pesagem;
import com.sementelivre.backend.entity.enums.TipoMovimentacao;
import com.sementelivre.backend.repository.EstoqueRepository;
import com.sementelivre.backend.repository.ProdutoRepository;
import com.sementelivre.backend.exception.RecursoNaoEncontradoException;
import com.sementelivre.backend.repository.MovimentacaoRepository;

@ExtendWith(MockitoExtension.class)
class EstoqueServiceTest {

    @Mock
    private EstoqueRepository estoqueRepository;

    @Mock
    private ProdutoRepository produtoRepository;

    @Mock
    private MovimentacaoRepository movimentacaoRepository;

    private EstoqueService estoqueService;

    private UUID estoqueId;
    private UUID produtoId;
    private UUID proprietarioId;

    private Produto produto;
    private Proprietario proprietario;
    private Estoque estoque;
    private EstoqueRequestDTO dto;

    @BeforeEach
    void setUp() {
        estoqueService = new EstoqueService(
                estoqueRepository,
                produtoRepository,
                movimentacaoRepository
        );

        estoqueId = UUID.randomUUID();
        produtoId = UUID.randomUUID();
        proprietarioId = UUID.randomUUID();

        produto = Produto.builder()
                .id(produtoId)
                .build();

        proprietario = new Proprietario();
        proprietario.setId(proprietarioId);

        estoque = Estoque.builder()
                .id(estoqueId)
                .produto(produto)
                .proprietario(proprietario)
                .descricao("Estoque de milho")
                .preco(15.0)
                .quantidade(10.0)
                .tipoPesagem(Pesagem.KG)
                .disponibilidade(Disponibilidade.PARA_VENDA)
                .tipoMovimentacao(TipoMovimentacao.ENTRADA)
                .dataMovimentacao(LocalDateTime.now())
                .dataUltimaAtualizacao(LocalDateTime.now())
                .build();

        dto = new EstoqueRequestDTO(
                proprietarioId,
                produtoId,
                "Estoque de milho",
                15.0,
                10.0,
                Pesagem.KG,
                Disponibilidade.PARA_VENDA,
                TipoMovimentacao.ENTRADA
        );
    }

    @Test
    void deveCriarEstoqueComSucesso() {
        when(produtoRepository.findById(produtoId))
                .thenReturn(Optional.of(produto));

        when(estoqueRepository.save(any(Estoque.class)))
                .thenAnswer(invocation -> {
                    Estoque estoqueSalvo = invocation.getArgument(0);
                    estoqueSalvo.setId(estoqueId);
                    return estoqueSalvo;
                });

        EstoqueResponseDTO resposta = estoqueService.criar(dto);

        assertNotNull(resposta);
        assertEquals(estoqueId, resposta.id());
        assertEquals(produtoId, resposta.produtoId());
        assertEquals(proprietarioId, resposta.proprietarioId());
        assertEquals(15.0, resposta.preco());
        assertEquals(10.0, resposta.quantidade());

        verify(produtoRepository).findById(produtoId);
        verify(estoqueRepository).save(any(Estoque.class));
    }

    @Test
    void deveListarTodosOsEstoques() {
        when(estoqueRepository.findAll())
                .thenReturn(List.of(estoque));

        List<EstoqueResponseDTO> resultado = estoqueService.listarTodos();

        assertEquals(1, resultado.size());
        assertEquals(estoqueId, resultado.get(0).id());
        assertEquals(produtoId, resultado.get(0).produtoId());

        verify(estoqueRepository).findAll();
    }

    @Test
    void deveBuscarEstoquePorId() {
        when(estoqueRepository.findById(estoqueId))
                .thenReturn(Optional.of(estoque));

        EstoqueResponseDTO resposta = estoqueService.buscarPorId(estoqueId);

        assertNotNull(resposta);
        assertEquals(estoqueId, resposta.id());
        assertEquals(produtoId, resposta.produtoId());
        assertEquals(proprietarioId, resposta.proprietarioId());

        verify(estoqueRepository).findById(estoqueId);
    }

    @Test
    void deveLancarExcecaoAoBuscarEstoqueInexistente() {
        when(estoqueRepository.findById(estoqueId))
                .thenReturn(Optional.empty());

        assertThrows(
                RecursoNaoEncontradoException.class,
                () -> estoqueService.buscarPorId(estoqueId)
        );

        verify(estoqueRepository).findById(estoqueId);
    }

    @Test
    void deveAtualizarEstoqueComSucesso() {
        EstoqueRequestDTO novoDto = new EstoqueRequestDTO(
                proprietarioId,
                produtoId,
                "Estoque atualizado",
                20.0,
                25.0,
                Pesagem.KG,
                Disponibilidade.PARA_TROCA,
                TipoMovimentacao.ENTRADA
        );

        when(estoqueRepository.findById(estoqueId))
                .thenReturn(Optional.of(estoque));

        when(produtoRepository.findById(produtoId))
                .thenReturn(Optional.of(produto));

        when(estoqueRepository.save(any(Estoque.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        EstoqueResponseDTO resposta =
                estoqueService.atualizar(estoqueId, novoDto);

        assertNotNull(resposta);
        assertEquals("Estoque atualizado", resposta.descricao());
        assertEquals(20.0, resposta.preco());
        assertEquals(25.0, resposta.quantidade());
        assertEquals(
                Disponibilidade.PARA_TROCA,
                resposta.disponibilidade()
        );

        verify(estoqueRepository).findById(estoqueId);
        verify(produtoRepository).findById(produtoId);
        verify(estoqueRepository).save(estoque);
    }

    // NEGOCIAÇÃO

    @Test
    void ofertaNovaAceitaNegociarQuandoNaoInformado() {
        mockCriacao();

        assertTrue(estoqueService.criar(dto).aceitaNegociacao());
    }

    @Test
    void deveCriarOfertaSemNegociacao() {
        mockCriacao();

        assertFalse(estoqueService.criar(comNegociacao(false)).aceitaNegociacao());
    }

    @Test
    void atualizarSemInformarNegociacaoMantemOQueEstava() {
        estoque.setAceitaNegociacao(false);
        mockAtualizacao();

        assertFalse(estoqueService.atualizar(estoqueId, dto).aceitaNegociacao());
    }

    @Test
    void deveBloquearNegociacaoAoAtualizar() {
        mockAtualizacao();

        assertFalse(estoqueService.atualizar(estoqueId, comNegociacao(false)).aceitaNegociacao());
        assertFalse(estoque.isAceitaNegociacao());
    }

    private EstoqueRequestDTO comNegociacao(boolean aceita) {
        return new EstoqueRequestDTO(proprietarioId, produtoId, "Estoque de milho", 15.0, 10.0,
                Pesagem.KG, Disponibilidade.PARA_VENDA, TipoMovimentacao.ENTRADA, aceita);
    }

    private void mockCriacao() {
        when(produtoRepository.findById(produtoId)).thenReturn(Optional.of(produto));
        when(estoqueRepository.save(any(Estoque.class))).thenAnswer(invocation -> {
            Estoque salvo = invocation.getArgument(0);
            salvo.setId(estoqueId);
            return salvo;
        });
    }

    private void mockAtualizacao() {
        when(estoqueRepository.findById(estoqueId)).thenReturn(Optional.of(estoque));
        when(produtoRepository.findById(produtoId)).thenReturn(Optional.of(produto));
        when(estoqueRepository.save(any(Estoque.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void deveExcluirEstoqueComSucesso() {
        when(estoqueRepository.findById(estoqueId))
                .thenReturn(Optional.of(estoque));

        estoqueService.excluir(estoqueId);

        verify(estoqueRepository).findById(estoqueId);
        verify(estoqueRepository).delete(estoque);
    }

    @Test
    void naoDeveCriarEstoqueQuandoProdutoNaoExiste() {
        when(produtoRepository.findById(produtoId))
                .thenReturn(Optional.empty());

        assertThrows(
                RecursoNaoEncontradoException.class,
                () -> estoqueService.criar(dto)
        );

        verify(produtoRepository).findById(produtoId);
        verify(estoqueRepository, never()).save(any(Estoque.class));
    }
}