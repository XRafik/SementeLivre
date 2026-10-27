package com.sementelivre.backend.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.sementelivre.backend.entity.Relatorio;
import com.sementelivre.backend.entity.enums.TipoRelatorio;

/**
 * Repositório de {@link Relatorio}.
 *
 * <p>Mesma ideia do {@link NotificacaoRepository}: estende
 * {@link BaseRepository} para herdar o CRUD e a paginação, e declara apenas as
 * consultas específicas do domínio de relatórios.</p>
 */
@Repository
public interface RelatorioRepository extends BaseRepository<Relatorio, UUID> {

    /**
     * Histórico de relatórios gerados por um proprietário, do mais recente para
     * o mais antigo.
     */
    List<Relatorio> findByProprietarioIdOrderByDataGeracaoDesc(UUID proprietarioId);

    /**
     * Mesmo histórico, filtrado por tipo de relatório (CDU-25).
     */
    List<Relatorio> findByProprietarioIdAndTipoOrderByDataGeracaoDesc(UUID proprietarioId, TipoRelatorio tipo);

    /** Remove o histórico de relatórios do proprietário na exclusão da conta (LGPD). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Relatorio r where r.proprietario.id = :proprietarioId")
    void deleteAllByProprietarioId(@Param("proprietarioId") UUID proprietarioId);

    long countByProprietarioId(UUID proprietarioId);
}
