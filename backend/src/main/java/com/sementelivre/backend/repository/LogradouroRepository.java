package com.sementelivre.backend.repository;

import com.sementelivre.backend.entity.Logradouro;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.UUID;

@Repository
public interface LogradouroRepository extends BaseRepository<Logradouro, UUID> {

    /**
     * Remove, entre os endereços informados, os que ninguém mais usa. As FKs para
     * logradouro_t são ON DELETE SET NULL, então excluir a pessoa ou a propriedade
     * deixava o endereço para trás -- dado pessoal que a exclusão de conta (LGPD)
     * precisa levar junto.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from Logradouro l
            where l.id in :ids
              and not exists (select p from Pessoa p where p.logradouro = l)
              and not exists (select c from Comunidade c where c.logradouro = l)
              and not exists (select pr from Propriedade pr where pr.logradouro = l)
            """)
    void deleteSemUsoByIdIn(@Param("ids") Collection<UUID> ids);
}
