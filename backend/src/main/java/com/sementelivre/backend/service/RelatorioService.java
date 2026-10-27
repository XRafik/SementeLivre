package com.sementelivre.backend.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.sementelivre.backend.dto.RelatorioRequestDTO;
import com.sementelivre.backend.dto.RelatorioResponseDTO;
import com.sementelivre.backend.entity.Proprietario;
import com.sementelivre.backend.entity.Relatorio;
import com.sementelivre.backend.exception.RecursoNaoEncontradoException;
import com.sementelivre.backend.repository.RelatorioRepository;
import com.sementelivre.backend.util.UsuarioLogado;

import jakarta.persistence.EntityManager;

@Service
public class RelatorioService
        implements CrudService<RelatorioRequestDTO, RelatorioResponseDTO, UUID> {

    private final RelatorioRepository relatorioRepository;
    private final EntityManager entityManager;

    public RelatorioService(
            RelatorioRepository relatorioRepository,
            EntityManager entityManager) {

        this.relatorioRepository = relatorioRepository;
        this.entityManager = entityManager;
    }

    // CREATE
    //
    // Cada um so registra relatorio no proprio nome.
    @Override
    public RelatorioResponseDTO criar(RelatorioRequestDTO dto) {

        if (!dto.proprietarioId().equals(UsuarioLogado.id())) {
            throw new RecursoNaoEncontradoException("Proprietário não encontrado: " + dto.proprietarioId());
        }

        Relatorio relatorio = Relatorio.builder()
                .tipo(dto.tipo())
                .filtrosUtilizados(dto.filtrosUtilizados())
                .proprietario(buscarProprietario(dto.proprietarioId()))
                .build();

        // saveAndFlush grava na hora, assim a dataGeracao ja volta preenchida
        Relatorio salvo = relatorioRepository.saveAndFlush(relatorio);

        return toResponseDTO(salvo);
    }

    // READ - por ID
    @Override
    public RelatorioResponseDTO buscarPorId(UUID id) {
        Relatorio relatorio = buscarEntidadeDoDono(id);

        return toResponseDTO(relatorio);
    }

    // READ - historico do usuario logado, do mais recente para o mais antigo.
    // Antes devolvia findAll(): qualquer pessoa logada via o historico de todos.
    @Override
    public List<RelatorioResponseDTO> listar() {
        return relatorioRepository.findByProprietarioIdOrderByDataGeracaoDesc(UsuarioLogado.id())
                .stream()
                .map(this::toResponseDTO)
                .toList();
    }

    // UPDATE
    //
    // O dono nao muda: trocar o proprietarioId permitiria passar o relatorio
    // para outra pessoa, entao o campo e ignorado de proposito.
    @Override
    public RelatorioResponseDTO atualizar(UUID id, RelatorioRequestDTO dto) {

        Relatorio relatorio = buscarEntidadeDoDono(id);

        relatorio.setTipo(dto.tipo());
        relatorio.setFiltrosUtilizados(dto.filtrosUtilizados());

        Relatorio atualizado = relatorioRepository.save(relatorio);

        return toResponseDTO(atualizado);
    }

    // DELETE
    @Override
    public void deletar(UUID id) {
        Relatorio relatorio = buscarEntidadeDoDono(id);

        relatorioRepository.delete(relatorio);
    }

    // Carrega o relatorio so se ele for do usuario logado. Devolve 404 (e nao
    // 403), igual ao NotificacaoService, para nao confirmar que o id existe.
    private Relatorio buscarEntidadeDoDono(UUID id) {
        Relatorio relatorio = relatorioRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Relatório não encontrado: " + id
                ));

        if (!relatorio.getProprietario().getId().equals(UsuarioLogado.id())) {
            throw new RecursoNaoEncontradoException("Relatório não encontrado: " + id);
        }

        return relatorio;
    }

    // Proprietario ainda nao tem repository (e placeholder),
    // entao buscamos direto pelo EntityManager
    private Proprietario buscarProprietario(UUID proprietarioId) {
        Proprietario proprietario = entityManager.find(Proprietario.class, proprietarioId);

        if (proprietario == null) {
            throw new RecursoNaoEncontradoException("Proprietário não encontrado: " + proprietarioId);
        }

        return proprietario;
    }

    private RelatorioResponseDTO toResponseDTO(Relatorio relatorio) {
        return new RelatorioResponseDTO(
                relatorio.getId(),
                relatorio.getTipo(),
                relatorio.getFiltrosUtilizados(),
                relatorio.getDataGeracao(),
                relatorio.getProprietario().getId()
        );
    }
}
