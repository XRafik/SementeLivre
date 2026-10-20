package com.sementelivre.backend.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sementelivre.backend.dto.ComunidadePublicaResponseDTO;
import com.sementelivre.backend.dto.ProdutorPublicoResponseDTO;
import com.sementelivre.backend.dto.SementePublicaResponseDTO;
import com.sementelivre.backend.entity.Estoque;
import com.sementelivre.backend.entity.Logradouro;
import com.sementelivre.backend.entity.Produto;
import com.sementelivre.backend.entity.Proprietario;
import com.sementelivre.backend.repository.EstoqueRepository;
import com.sementelivre.backend.exception.ResourceNotFoundException;
import com.sementelivre.backend.repository.PropriedadeRepository;
import com.sementelivre.backend.repository.ProprietarioRepository;
import org.springframework.cache.annotation.Cacheable;

/**
 * Perfil publico do produtor (issue #91) -- servido sem autenticacao.
 *
 * Regra de visibilidade, valida para todos os metodos publicos desta classe:
 * so aparece quem tem {@code exibirNoSitePublico = true}. Produtor inexistente
 * e produtor privado produzem exatamente a mesma resposta (404, mesma
 * mensagem), de proposito: distinguir os dois casos revelaria que a conta
 * existe.
 *
 * O mapeamento e manual, seguindo o padrao dos services mais novos do modulo
 * (ProdutoService, EstoqueService). Nenhuma entidade sai daqui: os metodos
 * publicos so devolvem os records de dto/*Publico*.
 */
@Service
public class ProdutorPublicoService {

    private final ProprietarioRepository proprietarioRepository;
    private final PropriedadeRepository propriedadeRepository;
    private final EstoqueRepository estoqueRepository;

    public ProdutorPublicoService(
            ProprietarioRepository proprietarioRepository,
            PropriedadeRepository propriedadeRepository,
            EstoqueRepository estoqueRepository) {

        this.proprietarioRepository = proprietarioRepository;
        this.propriedadeRepository = propriedadeRepository;
        this.estoqueRepository = estoqueRepository;
    }

    /**
     * Perfil publico completo: identificacao, municipio, comunidades e as
     * sementes visiveis.
     *
     * @throws ResourceNotFoundException se o produtor nao existe ou nao optou
     *         por aparecer no site publico
     */
    @Cacheable(value = "perfil_publico")
    @Transactional(readOnly = true)
    public ProdutorPublicoResponseDTO buscarPerfilPublico(UUID produtorId) {
        Proprietario produtor = buscarProdutorVisivel(produtorId);

        return new ProdutorPublicoResponseDTO(
                produtor.getId(),
                produtor.getNome(),
                municipioDe(produtor),
                listarComunidades(produtorId),
                listarSementesVisiveis(produtorId));
    }

    /**
     * Sementes/mudas do produtor que podem aparecer no site publico.
     *
     * Produtor publico sem estoque visivel devolve lista vazia (200), nao 404 --
     * 404 aqui e reservado para "o produtor nao existe ou nao e publico".
     *
     * @throws ResourceNotFoundException se o produtor nao existe ou nao optou
     *         por aparecer no site publico
     */
    @Cacheable(value = "sementes_publicas")
    @Transactional(readOnly = true)
    public List<SementePublicaResponseDTO> listarSementes(UUID produtorId) {
        // Valida a visibilidade antes de listar: sem isso, o endpoint de
        // sementes viraria um jeito de descobrir produtores privados.
        buscarProdutorVisivel(produtorId);
        return listarSementesVisiveis(produtorId);
    }

    private Proprietario buscarProdutorVisivel(UUID produtorId) {
        return proprietarioRepository.findByIdAndExibirNoSitePublicoTrue(produtorId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Produtor não encontrado: " + produtorId));
    }

    private List<ComunidadePublicaResponseDTO> listarComunidades(UUID produtorId) {
        return propriedadeRepository.findComunidadesByProprietarioId(produtorId).stream()
                .map(comunidade -> new ComunidadePublicaResponseDTO(
                        comunidade.getId(),
                        comunidade.getNome()))
                .toList();
    }

    private List<SementePublicaResponseDTO> listarSementesVisiveis(UUID produtorId) {
        return estoqueRepository.findVisiveisNoSitePublico(produtorId).stream()
                .map(this::mapToSementePublica)
                .toList();
    }

    private SementePublicaResponseDTO mapToSementePublica(Estoque estoque) {
        Produto produto = estoque.getProduto();

        return new SementePublicaResponseDTO(
                produto.getId(),
                produto.getNomePopular(),
                produto.getNomeCientifico(),
                produto.getFormato(),
                produto.getTipo(),
                produto.getFamiliaBotanica(),
                produto.getUrlFoto(),
                estoque.getDisponibilidade());
    }

    /**
     * Do endereco do produtor sai apenas o municipio (LGPD). Pessoa.logradouro
     * e opcional, entao municipio pode vir nulo.
     */
    private String municipioDe(Proprietario produtor) {
        Logradouro endereco = produtor.getLogradouro();
        return endereco == null ? null : endereco.getMunicipio();
    }
}
