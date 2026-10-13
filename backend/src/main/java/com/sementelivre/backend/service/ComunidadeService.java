package com.sementelivre.backend.service;

import com.sementelivre.backend.dto.ComunidadeRequestDTO;
import com.sementelivre.backend.dto.ComunidadeResponseDTO;
import com.sementelivre.backend.entity.Comunidade;
import com.sementelivre.backend.entity.Logradouro;
import com.sementelivre.backend.entity.enums.StatusComunidade;
import com.sementelivre.backend.exception.DependenciaVinculadaException;
import com.sementelivre.backend.exception.NomeSimilarException;
import com.sementelivre.backend.exception.ResourceNotFoundException;
import com.sementelivre.backend.exception.TransicaoStatusInvalidaException;
import com.sementelivre.backend.repository.ComunidadeRepository;
import com.sementelivre.backend.repository.LogradouroRepository;
import com.sementelivre.backend.repository.PropriedadeRepository;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class ComunidadeService implements CrudService<ComunidadeRequestDTO, ComunidadeResponseDTO, UUID> {

    private final ComunidadeRepository comunidadeRepository;
    private final LogradouroRepository logradouroRepository;
    private final PropriedadeRepository propriedadeRepository;
    private static final LevenshteinDistance LEVENSHTEIN = new LevenshteinDistance();
    private static final double LIMITE_SIMILARIDADE = 0.2; // 20% do maior nome

    public ComunidadeService(ComunidadeRepository comunidadeRepository, LogradouroRepository logradouroRepository, PropriedadeRepository propriedadeRepository) {
        this.comunidadeRepository = comunidadeRepository;
        this.logradouroRepository = logradouroRepository;
        this.propriedadeRepository = propriedadeRepository;
    }

    //Criar
    @Override
    @Transactional // o logradouro novo não fica órfão se a validação falhar
    @CacheEvict(value = "comunidades", allEntries = true)
    public ComunidadeResponseDTO criar(ComunidadeRequestDTO dto) {
        Logradouro logradouro = resolverLogradouro(dto, null);

        List<String> listaNomes = comunidadeRepository.findAllNames();
        String novoNome = dto.nome().toLowerCase(Locale.ROOT);

        validarNomeNaoSimilar(dto.nome(), logradouro, comunidadeRepository.findAllNames());

        Comunidade comunidade = Comunidade.builder()
                .nome(dto.nome())
                .logradouro(logradouro)
                .status(StatusComunidade.PENDENTE_APROVACAO)//Coloquei o status padrão de criação como pendente aprovação
                .dataSolicitacao(LocalDateTime.now())
                .build();

        Comunidade comunidadeSalva = comunidadeRepository.save(comunidade);

        return toResponseDTO(comunidadeSalva);
    }

    @Override
    public ComunidadeResponseDTO buscarPorId(UUID id) {

        Comunidade comunidade =  buscarEntidadePorId(id);

        return toResponseDTO(comunidade);
    }

    @Override
    @Cacheable(value = "comunidades")
    public List<ComunidadeResponseDTO> listar() {
        return comunidadeRepository.findAll()
                .stream()
                .map(this::toResponseDTO)
                .toList();
    }

    @Override
    @Transactional // o logradouro novo não fica órfão se a validação falhar
    @CacheEvict(value = "comunidades", allEntries = true)
    public ComunidadeResponseDTO atualizar(UUID id, ComunidadeRequestDTO dto) {
        Comunidade comunidade = buscarEntidadePorId(id);

        Logradouro logradouro = comunidade.getLogradouro();

        //Verificação para caso o logradouro tenha sido atualizado também; sem endereço nem id, mantém o atual
        logradouro = resolverLogradouro(dto, logradouro);

        if (!comunidade.getNome().equalsIgnoreCase(dto.nome())) {
            validarNomeNaoSimilar(dto.nome(), logradouro, comunidadeRepository.findAllNamesExceto(id));
        }

        //Preenchendo comunidade:
        comunidade.setNome(dto.nome());
        comunidade.setLogradouro(logradouro);

        Comunidade comunidadeAtualizada = comunidadeRepository.save(comunidade);
        return toResponseDTO(comunidadeAtualizada);
    }

    @Override
    @CacheEvict(value = "comunidades", allEntries = true)
    public void deletar(UUID id) {

        Comunidade comunidade = buscarEntidadePorId(id);

        List<String> propriedades = propriedadeRepository.findNomesByComunidadeId(id);
        if (!propriedades.isEmpty()) {
            throw new DependenciaVinculadaException(
                    "Não é possível excluir a Comunidade \"" + comunidade.getNome() + "\", pois existem "
                            + propriedades.size() + " propriedade(s) vinculada(s): " + String.join(", ", propriedades) + ".");
        }
        comunidadeRepository.delete(comunidade);
    }

    /**
     * O `endereco` enviado vira um logradouro novo; sem ele vale o `logradouroId`;
     * sem nenhum dos dois, a atualização mantém o que já havia.
     */
    private Logradouro resolverLogradouro(ComunidadeRequestDTO dto, Logradouro atual) {
        if (dto.endereco() != null) {
            return logradouroRepository.save(dto.endereco().paraEntidade());
        }
        if (dto.logradouroId() != null) {
            if (atual != null && atual.getId().equals(dto.logradouroId())) return atual;
            return logradouroRepository.findById(dto.logradouroId())
                    .orElseThrow(() -> new ResourceNotFoundException("Logradouro não encontrado com o id: " + dto.logradouroId()));
        }
        if (atual != null) return atual;
        throw new IllegalArgumentException("Informe o endereço da comunidade.");
    }

    //Transformando de entidade comunidade para ComunidadeResponseDTO
    private ComunidadeResponseDTO toResponseDTO(Comunidade comunidade){

        return new ComunidadeResponseDTO(
                comunidade.getId(),
                comunidade.getNome(),
                comunidade.getStatus(),
                comunidade.getDataSolicitacao(),
                comunidade.getDataAprovacao(),
                comunidade.getLogradouro().getUf(),
                comunidade.getLogradouro().getMunicipio()
        );
    }

    //Metodos de Aprovação e Rejeição de Comunidades
    @CacheEvict(value = "comunidades", allEntries = true)
    public ComunidadeResponseDTO aprovar(UUID id){
        Comunidade comunidade = buscarPendente(id, "aprovadas");
        comunidade.setStatus(StatusComunidade.ATIVA);
        comunidade.setDataAprovacao(LocalDateTime.now());

        Comunidade comunidadeAprovada = comunidadeRepository.save(comunidade);
        return toResponseDTO(comunidadeAprovada);
    }
    
    @CacheEvict(value = "comunidades", allEntries = true)
    public ComunidadeResponseDTO rejeitar(UUID id){
        Comunidade comunidade = buscarPendente(id, "rejeitadas");
        comunidade.setStatus(StatusComunidade.REJEITADA);

        Comunidade comunidadeRejeitada = comunidadeRepository.save(comunidade);
        return toResponseDTO(comunidadeRejeitada);
    }

    //Metodo para buscar uma Entidade Comunidade
    private Comunidade buscarEntidadePorId(UUID id){
        return comunidadeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Comunidade não encontrada, com id: " + id));
    }


    // Só comunidades pendentes podem ser aprovadas/rejeitadas
    private Comunidade buscarPendente(UUID id, String acao) {
        Comunidade comunidade = buscarEntidadePorId(id);
        if (comunidade.getStatus() != StatusComunidade.PENDENTE_APROVACAO) {
            throw new TransicaoStatusInvalidaException(
                    "Somente comunidades com status PENDENTE_APROVACAO podem ser " + acao
                            + ". Status atual: " + comunidade.getStatus() + ".");
        }
        return comunidade;
    }

    private void validarNomeNaoSimilar(String nome, Logradouro logradouro, List<String> nomesExistentes) {
        String novoNome = normalizar(nome);
        nomesExistentes.stream()
                .filter(existente -> saoSimilares(novoNome, normalizar(existente)))
                .findFirst()
                .ifPresent(existente -> {
                    throw new NomeSimilarException(
                            "O nome \"" + nome + "\" é muito similar ao nome já existente \"" + existente
                                    + "\". Sugestão: \"" + nome + " - " + logradouro.getMunicipio() + "\".");
                });
    }

    private boolean saoSimilares(String a, String b) {
        int distancia = LEVENSHTEIN.apply(a, b);
        double limite = LIMITE_SIMILARIDADE * Math.max(a.length(), b.length());
        return distancia <= limite;
    }

    // minúsculas, sem acentos e sem espaços repetidos
    private String normalizar(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }
}