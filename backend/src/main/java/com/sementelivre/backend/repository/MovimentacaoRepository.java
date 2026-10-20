package com.sementelivre.backend.repository;

import com.sementelivre.backend.entity.Movimentacao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface MovimentacaoRepository extends BaseRepository<Movimentacao, UUID> {

    /**
     * Histórico de um estoque, do mais recente para o mais antigo.
     *
     * Filtra por estoque_id direto no banco (não por e.id) para aproveitar o
     * índice e evitar carregar a entidade Estoque só para comparar o id.
     */
    @Query("""
            select m from Movimentacao m
            left join fetch m.usuario u
            where m.estoque.id = :estoqueId
            order by m.dataMovimentacao desc, m.id desc
            """)
    Page<Movimentacao> buscarPorEstoque(
            @Param("estoqueId") UUID estoqueId,
            Pageable pageable);
}
