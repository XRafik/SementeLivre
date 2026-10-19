package com.sementelivre.backend.repository;

import com.sementelivre.backend.entity.Comunidade;
import com.sementelivre.backend.entity.Propriedade;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PropriedadeRepository extends BaseRepository<Propriedade, UUID> {

    List<Propriedade> findByProprietarioIdOrderByDataCadastroDesc(UUID proprietarioId);

    boolean existsByIdAndProprietarioId(UUID id, UUID proprietarioId);

    /**
     * Comunidades de um produtor (issue #91).
     *
     * Nao existe relacao direta entre Proprietario e Comunidade: o vinculo
     * passa por Propriedade, que tem @ManyToOne para os dois. Como nenhum dos
     * lados expoe @OneToMany, o caminho so existe por consulta.
     *
     * O distinct e necessario porque um produtor pode ter varias propriedades
     * na mesma comunidade.
     */
    @Query("""
            select distinct c from Propriedade p
            join p.comunidade c
            where p.proprietario.id = :proprietarioId
            order by c.nome
            """)
    List<Comunidade> findComunidadesByProprietarioId(@Param("proprietarioId") UUID proprietarioId);

    @Query("select p.nome from Propriedade p where p.comunidade.id = :comunidadeId order by p.nome")
    List<String> findNomesByComunidadeId(@Param("comunidadeId") UUID comunidadeId);

    /** Remove as propriedades do produtor na exclusão da conta (LGPD). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Propriedade p where p.proprietario.id = :proprietarioId")
    void deleteAllByProprietarioId(@Param("proprietarioId") UUID proprietarioId);

    long countByProprietarioId(UUID proprietarioId);

    @Query("select p.logradouro.id from Propriedade p where p.proprietario.id = :proprietarioId")
    List<UUID> findLogradouroIdsByProprietarioId(@Param("proprietarioId") UUID proprietarioId);

}
