package com.sementelivre.backend.service;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.sementelivre.backend.dto.CatalogoOfertaResponseDTO;
import com.sementelivre.backend.dto.CatalogoProdutoDetalheResponseDTO;
import com.sementelivre.backend.dto.CatalogoProdutoResponseDTO;
import com.sementelivre.backend.entity.Comunidade;
import com.sementelivre.backend.entity.Estoque;
import com.sementelivre.backend.entity.Logradouro;
import com.sementelivre.backend.entity.Produto;
import com.sementelivre.backend.entity.enums.Disponibilidade;
import com.sementelivre.backend.entity.enums.EspecieGeral;
import com.sementelivre.backend.entity.enums.TipoProduto;
import com.sementelivre.backend.repository.EstoqueRepository;
import com.sementelivre.backend.exception.RecursoNaoEncontradoException;

@Service
public class CatalogoPublicoService {

    /*
     * Disponibilidades que podem aparecer no catalogo publico.
     *
     * A_NEGOCIAR e INDISPONIVEL ficam fora do catalogo da issue #93.
     */
    private static final List<Disponibilidade> DISPONIBILIDADES_PUBLICAS =
            List.of(
                    Disponibilidade.PARA_VENDA,
                    Disponibilidade.PARA_TROCA,
                    Disponibilidade.PARA_DOACAO
            );

    private final EstoqueRepository estoqueRepository;

    public CatalogoPublicoService(EstoqueRepository estoqueRepository) {
        this.estoqueRepository = estoqueRepository;
    }

    public Page<CatalogoProdutoResponseDTO> listar(
            String nomePopular,
            TipoProduto tipo,
            EspecieGeral especie,
            Disponibilidade disponibilidade,
            String comunidade,
            String municipio,
            Pageable pageable) {

        return estoqueRepository.buscarCatalogoPublico(
                DISPONIBILIDADES_PUBLICAS,
                normalizar(nomePopular),
                tipo != null ? List.of(tipo) : List.of(TipoProduto.values()),
                especie != null ? List.of(especie) : List.of(EspecieGeral.values()),
                disponibilidade != null
                        ? List.of(disponibilidade)
                        : DISPONIBILIDADES_PUBLICAS,
                normalizar(comunidade),
                normalizar(municipio),
                pageable
        ).map(this::toResponseDTO);
    }

    public CatalogoProdutoDetalheResponseDTO buscarPorProdutoId(
            UUID produtoId) {

        List<Estoque> estoques =
                estoqueRepository.buscarProdutosPublicosPorId(
                        produtoId,
                        DISPONIBILIDADES_PUBLICAS
                );

        if (estoques.isEmpty()) {
            throw new RecursoNaoEncontradoException(
                    "Produto nao encontrado no catalogo publico: " + produtoId
            );
        }

        Produto produto = estoques.get(0).getProduto();

        Comunidade comunidade = produto.getComunidadeOrigem();

        Logradouro logradouro = comunidade != null
                ? comunidade.getLogradouro()
                : null;

        List<CatalogoOfertaResponseDTO> ofertas = estoques.stream()
                .map(estoque -> new CatalogoOfertaResponseDTO(
                        estoque.getId(),
                        estoque.getDescricao(),
                        estoque.getPreco(),
                        estoque.getQuantidade(),
                        estoque.getTipoPesagem(),
                        estoque.getDisponibilidade(),
                        estoque.isAceitaNegociacao()
                ))
                .toList();

        return new CatalogoProdutoDetalheResponseDTO(
                produto.getId(),
                produto.getNomePopular(),
                produto.getNomeCientifico(),
                produto.getHistorico(),
                produto.getUrlFoto(),
                produto.getTipo(),
                produto.getEspecie(),
                produto.getFormato(),
                produto.getFamiliaBotanica(),
                comunidade != null ? comunidade.getId() : null,
                comunidade != null ? comunidade.getNome() : null,
                logradouro != null ? logradouro.getMunicipio() : null,
                logradouro != null ? logradouro.getUf() : null,
                ofertas
        );
    }

    private CatalogoProdutoResponseDTO toResponseDTO(
            Estoque estoque) {

        Produto produto = estoque.getProduto();

        Comunidade comunidade = produto.getComunidadeOrigem();

        Logradouro logradouro = comunidade != null
                ? comunidade.getLogradouro()
                : null;

        UUID comunidadeId = comunidade != null
                ? comunidade.getId()
                : null;

        String nomeComunidade = comunidade != null
                ? comunidade.getNome()
                : null;

        String municipio = logradouro != null
                ? logradouro.getMunicipio()
                : null;

        String uf = logradouro != null
                ? logradouro.getUf()
                : null;

        return new CatalogoProdutoResponseDTO(
                produto.getId(),
                produto.getNomePopular(),
                produto.getNomeCientifico(),
                produto.getHistorico(),
                produto.getUrlFoto(),
                produto.getTipo(),
                produto.getEspecie(),
                produto.getFormato(),
                produto.getFamiliaBotanica(),
                estoque.getId(),
                estoque.getDescricao(),
                estoque.getPreco(),
                estoque.getQuantidade(),
                estoque.getTipoPesagem(),
                estoque.getDisponibilidade(),
                comunidadeId,
                nomeComunidade,
                municipio,
                uf,
                estoque.isAceitaNegociacao()
        );
    }

    private String normalizar(String valor) {

        if (valor == null || valor.isBlank()) {
            return "";
        }

        return valor.trim().toLowerCase();
    }
}