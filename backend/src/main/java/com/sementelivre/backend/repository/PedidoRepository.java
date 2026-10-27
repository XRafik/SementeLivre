package com.sementelivre.backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sementelivre.backend.entity.Pedido;

import jakarta.persistence.LockModeType;

public interface PedidoRepository extends BaseRepository<Pedido, UUID> {

    // Carrega os itens junto para evitar N+1 na listagem.
    // distinct porque o join com itens multiplica as linhas de pedido.
    //lista pedidos isolando apenas os que o proprietario recebeu
    @Query("""
            select distinct p from Pedido p
            left join fetch p.itens i
            left join fetch i.produto
            left join fetch p.comprador
            where p.proprietarioRecebedor.id = :proprietarioId
            """)
    List<Pedido> findAllByProprietarioRecebedorId(@Param("proprietarioId") UUID proprietarioId);

    //para uso interno (admin)
    @Query("""
            select distinct p from Pedido p
            left join fetch p.itens i
            left join fetch i.produto
            where p.usuarioSolicitante.id = :usuarioId
            """)
    List<Pedido> findAllByUsuarioSolicitanteId(@Param("usuarioId") UUID usuarioId);

    @Query("""
            select p from Pedido p
            left join fetch p.itens i
            left join fetch i.produto
            left join fetch p.comprador
            where p.id = :id
            """)
    Optional<Pedido> findByIdComItens(@Param("id") UUID id);

    long countByProprietarioRecebedorId(UUID proprietarioId);

    /**
     * Busca o pedido travando a linha (select ... for update) para as operacoes
     * que mudam status ou mexem no estoque. Sem o lock, dois cancelamentos
     * simultaneos leem o mesmo status PENDENTE, ambos passam pela validacao de
     * transicao e o estoque e restaurado duas vezes.
     *
     * Sem join fetch de proposito: o PostgreSQL nao aceita FOR UPDATE no lado
     * anulavel de um left join. Os itens carregam sob demanda na mesma transacao.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Pedido p where p.id = :id")
    Optional<Pedido> findParaAtualizacao(@Param("id") UUID id);

    /**
     * Exclui os pedidos em que a pessoa é solicitante ou recebedora. Os itens
     * precisam sair antes (ItensRepository.deleteAllDePedidosEnvolvendoPessoa):
     * itens_pedido_t.pedido_id é ON DELETE RESTRICT.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Pedido p where p.usuarioSolicitante.id = :id or p.proprietarioRecebedor.id = :id")
    void deleteAllEnvolvendoPessoa(@Param("id") UUID id);

    @Query("select count(p) from Pedido p where p.usuarioSolicitante.id = :id or p.proprietarioRecebedor.id = :id")
    long countEnvolvendoPessoa(@Param("id") UUID id);

    @Query("""
            select distinct p.comprador.id from Pedido p
            where (p.usuarioSolicitante.id = :id or p.proprietarioRecebedor.id = :id)
              and p.comprador is not null
            """)
    List<UUID> findCompradorIdsEnvolvendoPessoa(@Param("id") UUID id);
}
