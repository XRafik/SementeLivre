package com.sementelivre.backend.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

import com.sementelivre.backend.dto.ProdutoRequestDTO;
import com.sementelivre.backend.dto.ProdutoResponseDTO;
import com.sementelivre.backend.entity.Produto;
import com.sementelivre.backend.entity.enums.EspecieGeral;
import com.sementelivre.backend.entity.enums.FormatoProduto;
import com.sementelivre.backend.entity.enums.TipoProduto;
import com.sementelivre.backend.repository.ProdutoRepository;
import com.sementelivre.backend.exception.RecursoNaoEncontradoException;

@ExtendWith(MockitoExtension.class)
class ProdutoServiceTest {

    @Mock
    private ProdutoRepository produtoRepository;

    @Mock
    private ProdutoValidacaoService produtoValidacaoService;

    private ProdutoService produtoService;

    private UUID produtoId;
    private Produto produto;
    private ProdutoRequestDTO dto;

    @BeforeEach
    void setUp() {
        produtoService = new ProdutoService(
                produtoRepository,
                produtoValidacaoService
        );

        produtoId = UUID.randomUUID();

        produto = Produto.builder()
                .id(produtoId)
                .nomePopular("Milho Crioulo")
                .nomeCientifico("Zea mays")
                .historico("Produto de teste")
                .urlFoto("/uploads/produtos/milho.jpg")
                .tipo(TipoProduto.CEREAL)
                .especie(EspecieGeral.MILHO)
                .formato(FormatoProduto.SEMENTE)
                .familiaBotanica("Poaceae")
                .dataInclusao(LocalDateTime.now())
                .dataUltimaAlteracao(LocalDateTime.now())
                .build();

        dto = new ProdutoRequestDTO(
                "Milho Crioulo",
                "Zea mays",
                "Produto de teste",
                "/uploads/produtos/milho.jpg",
                TipoProduto.CEREAL,
                EspecieGeral.MILHO,
                FormatoProduto.SEMENTE,
                "Poaceae",
                null
        );
    }

    @Test
    void deveCriarProdutoComSucesso() {
        when(produtoRepository.save(any(Produto.class)))
                .thenAnswer(invocation -> {
                    Produto salvo = invocation.getArgument(0);
                    salvo.setId(produtoId);
                    return salvo;
                });

        ProdutoResponseDTO resposta = produtoService.criar(dto);

        assertNotNull(resposta);
        assertEquals(produtoId, resposta.id());
        assertEquals("Milho Crioulo", resposta.nomePopular());
        assertEquals(TipoProduto.CEREAL, resposta.tipo());
        assertEquals(EspecieGeral.MILHO, resposta.especie());

        verify(produtoValidacaoService)
                .validarTipoEspecie(TipoProduto.CEREAL, EspecieGeral.MILHO);

        verify(produtoRepository).save(any(Produto.class));
    }

    @Test
    void deveListarTodosOsProdutos() {
        when(produtoRepository.findAll())
                .thenReturn(List.of(produto));

        List<ProdutoResponseDTO> resposta =
                produtoService.listarTodos();

        assertEquals(1, resposta.size());
        assertEquals(produtoId, resposta.get(0).id());
        assertEquals("Milho Crioulo", resposta.get(0).nomePopular());

        verify(produtoRepository).findAll();
    }

    @Test
    void deveBuscarProdutoPorId() {
        when(produtoRepository.findById(produtoId))
                .thenReturn(Optional.of(produto));

        ProdutoResponseDTO resposta =
                produtoService.buscarPorId(produtoId);

        assertEquals(produtoId, resposta.id());
        assertEquals("Milho Crioulo", resposta.nomePopular());

        verify(produtoRepository).findById(produtoId);
    }

    @Test
    void deveLancarExcecaoAoBuscarProdutoInexistente() {
        when(produtoRepository.findById(produtoId))
                .thenReturn(Optional.empty());

        assertThrows(
                RecursoNaoEncontradoException.class,
                () -> produtoService.buscarPorId(produtoId)
        );

        verify(produtoRepository).findById(produtoId);
    }

    @Test
    void deveAtualizarProdutoComSucesso() {
        ProdutoRequestDTO dtoAtualizado =
                new ProdutoRequestDTO(
                        "Milho Atualizado",
                        "Zea mays",
                        "Histórico atualizado",
                        "/uploads/produtos/milho2.jpg",
                        TipoProduto.CEREAL,
                        EspecieGeral.MILHO,
                        FormatoProduto.SEMENTE,
                        "Poaceae",
                        null
                );

        when(produtoRepository.findById(produtoId))
                .thenReturn(Optional.of(produto));

        when(produtoRepository.save(any(Produto.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ProdutoResponseDTO resposta =
                produtoService.atualizar(produtoId, dtoAtualizado);

        assertEquals("Milho Atualizado", resposta.nomePopular());
        assertEquals(
                "/uploads/produtos/milho2.jpg",
                resposta.urlFoto()
        );

        verify(produtoValidacaoService)
                .validarTipoEspecie(
                        TipoProduto.CEREAL,
                        EspecieGeral.MILHO
                );

        verify(produtoRepository).save(produto);
    }

    @Test
    void deveExcluirProdutoComSucesso() {
        when(produtoRepository.findById(produtoId))
                .thenReturn(Optional.of(produto));

        produtoService.excluir(produtoId);

        verify(produtoRepository).delete(produto);
    }

    @Test
    void naoDeveSalvarProdutoQuandoValidacaoFalhar() {
        org.mockito.Mockito.doThrow(
                new IllegalArgumentException(
                        "Combinação inválida entre tipo e espécie"
                )
        ).when(produtoValidacaoService)
                .validarTipoEspecie(
                        TipoProduto.CEREAL,
                        EspecieGeral.MILHO
                );

        assertThrows(
                IllegalArgumentException.class,
                () -> produtoService.criar(dto)
        );

        verify(produtoRepository, never())
                .save(any(Produto.class));
    }
}