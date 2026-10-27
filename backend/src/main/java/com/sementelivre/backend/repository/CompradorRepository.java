package com.sementelivre.backend.repository;

import java.util.Collection;
import java.util.UUID;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sementelivre.backend.entity.Comprador;

public interface CompradorRepository extends BaseRepository<Comprador, UUID> {

    /**
     * Remove, entre os compradores informados, os que não estão mais em nenhum
     * pedido. Usado na exclusão de conta: nome e telefone do comprador são dados
     * pessoais e não podem ficar órfãos depois que os pedidos saem.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from Comprador c
            where c.id in :ids
              and not exists (select p from Pedido p where p.comprador = c)
            """)
    void deleteSemPedidoByIdIn(@Param("ids") Collection<UUID> ids);
}
