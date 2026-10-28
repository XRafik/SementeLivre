package com.sementelivre.backend.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.sementelivre.backend.dto.ProdutoRequestDTO;
import com.sementelivre.backend.dto.ProdutoResponseDTO;
import com.sementelivre.backend.entity.Produto;
import com.sementelivre.backend.repository.EstoqueRepository;
import com.sementelivre.backend.repository.ItensRepository;
import com.sementelivre.backend.repository.ProdutoRepository;
import com.sementelivre.backend.exception.RecursoNaoEncontradoException;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;

@Service
public class ProdutoService {

    private final ProdutoRepository produtoRepository;
    private final ProdutoValidacaoService produtoValidacaoService;
    private final EstoqueRepository estoqueRepository;
    private final ItensRepository itensRepository;

   public ProdutoService(
        ProdutoRepository produtoRepository,
        ProdutoValidacaoService produtoValidacaoService,
        EstoqueRepository estoqueRepository,
        ItensRepository itensRepository) {

    this.produtoRepository = produtoRepository;
    this.produtoValidacaoService = produtoValidacaoService;
    this.estoqueRepository = estoqueRepository;
    this.itensRepository = itensRepository;
}

    /**
     * Usado no @PreAuthorize do controller. O produto não guarda dono: cada
     * produtor cadastra o seu junto com a oferta. Quem pode alterar é quem não
     * mexe em dado alheio, ou seja, nenhuma oferta de outro produtor usa o
     * produto e ele não está no histórico de pedidos de outro produtor.
     */
    public boolean podeAlterar(UUID produtoId, UUID proprietarioId) {
        return !estoqueRepository.existsByProdutoIdAndProprietarioIdNot(produtoId, proprietarioId)
                && !itensRepository.existsEmPedidoDeOutroProprietario(produtoId, proprietarioId);
    }

    // CREATE
    @CacheEvict(value = "produtos", allEntries = true)
    public ProdutoResponseDTO criar(ProdutoRequestDTO dto) {


         produtoValidacaoService.validarTipoEspecie(
            dto.tipo(),
            dto.especie()
        );

        
        Produto produto = Produto.builder()
                .nomePopular(dto.nomePopular())
                .nomeCientifico(dto.nomeCientifico())
                .historico(dto.historico())
                .urlFoto(dto.urlFoto())
                .tipo(dto.tipo())
                .especie(dto.especie())
                .formato(dto.formato())
                .familiaBotanica(dto.familiaBotanica())
                .dataInclusao(LocalDateTime.now())
                .dataUltimaAlteracao(LocalDateTime.now())
                .build();

        Produto salvo = produtoRepository.save(produto);

        return toResponseDTO(salvo);
    }

    // READ - todos
    @Cacheable(value = "produtos")
    public List<ProdutoResponseDTO> listarTodos() {
        return produtoRepository.findAll()
                .stream()
                .map(this::toResponseDTO)
                .toList();
    }

    // READ - por ID
    public ProdutoResponseDTO buscarPorId(UUID id) {
        Produto produto = buscarEntidadePorId(id);

        return toResponseDTO(produto);
    }

    // UPDATE
    @CacheEvict(value = "produtos", allEntries = true)
    public ProdutoResponseDTO atualizar(UUID id, ProdutoRequestDTO dto) {

        Produto produto = buscarEntidadePorId(id);

         produtoValidacaoService.validarTipoEspecie(
            dto.tipo(),
            dto.especie()
    );

        produto.setNomePopular(dto.nomePopular());
        produto.setNomeCientifico(dto.nomeCientifico());
        produto.setHistorico(dto.historico());
        produto.setUrlFoto(dto.urlFoto());
        produto.setTipo(dto.tipo());
        produto.setEspecie(dto.especie());
        produto.setFormato(dto.formato());
        produto.setFamiliaBotanica(dto.familiaBotanica());

        produto.setDataUltimaAlteracao(LocalDateTime.now());



        Produto atualizado = produtoRepository.save(produto);

        return toResponseDTO(atualizado);
    }

    // DELETE
    @CacheEvict(value = "produtos", allEntries = true)
    public void excluir(UUID id) {
        Produto produto = buscarEntidadePorId(id);

        produtoRepository.delete(produto);
    }

    private Produto buscarEntidadePorId(UUID id) {
        return produtoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
        "Produto não encontrado: " + id
            ));
    }

    private ProdutoResponseDTO toResponseDTO(Produto produto) {

        UUID comunidadeOrigemId = null;

        if (produto.getComunidadeOrigem() != null) {
            comunidadeOrigemId = produto.getComunidadeOrigem().getId();
        }

        return new ProdutoResponseDTO(
                produto.getId(),
                produto.getNomePopular(),
                produto.getNomeCientifico(),
                produto.getHistorico(),
                produto.getUrlFoto(),
                produto.getTipo(),
                produto.getEspecie(),
                produto.getFormato(),
                produto.getFamiliaBotanica(),
                comunidadeOrigemId,
                produto.getDataInclusao(),
                produto.getDataUltimaAlteracao()
        );
    }
}