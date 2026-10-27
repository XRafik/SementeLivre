package com.sementelivre.backend.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sementelivre.backend.entity.Itens;

public interface ItensRepository extends BaseRepository<Itens, UUID> {

    /**
     * Itens dos pedidos em que a pessoa é solicitante ou recebedora, na exclusão
     * da conta (LGPD). Saem antes dos pedidos: itens_pedido_t.pedido_id é
     * ON DELETE RESTRICT.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from Itens i
            where i.pedido.id in (select p.id from Pedido p
                                  where p.usuarioSolicitante.id = :id or p.proprietarioRecebedor.id = :id)
            """)
    void deleteAllDePedidosEnvolvendoPessoa(@Param("id") UUID id);
}
