package com.sementelivre.backend.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.sementelivre.backend.entity.Notificacao;

/**
 * Repositório de {@link Notificacao}.
 *
 * <p>Estende {@link BaseRepository} (e não {@code JpaRepository} direto),
 * conforme a convenção definida na task de estrutura base do backend. Com isso
 * já vêm prontos {@code save}, {@code findById}, {@code findAll},
 * {@code delete} e paginação — não é preciso escrever nada disso.</p>
 *
 * <p>Os métodos abaixo são <i>derived queries</i>: o Spring Data lê o nome do
 * método e monta o SQL sozinho. A leitura é literal, da esquerda para a
 * direita, por exemplo
 * {@code findByProprietarioIdAndLidaFalseOrderByDataGeracaoDesc} =
 * "buscar onde proprietario.id = ? e lida = false, ordenando por dataGeracao
 * decrescente". Como o nome vira consulta, <b>renomear um método muda o SQL
 * gerado</b> — e um nome que não bate com os campos da entidade faz a aplicação
 * falhar já na subida do contexto.</p>
 */
@Repository
public interface NotificacaoRepository extends BaseRepository<Notificacao, UUID> {

    /**
     * Histórico completo de notificações de um proprietário, da mais recente
     * para a mais antiga.
     */
    List<Notificacao> findByProprietarioIdOrderByDataGeracaoDesc(UUID proprietarioId);

    /**
     * Apenas as notificações ainda não lidas — é o que alimenta o alerta visual
     * do CDU-26.
     */
    List<Notificacao> findByProprietarioIdAndLidaFalseOrderByDataGeracaoDesc(UUID proprietarioId);

    List<Notificacao> findByPedidoRelacionadoId(UUID pedidoId);

    /**
     * Quantidade de notificações não lidas, usada no "badge" com o número de
     * pendências. Contar no banco é bem mais barato do que trazer a lista
     * inteira só para chamar {@code size()}.
     */
    long countByProprietarioIdAndLidaFalse(UUID proprietarioId);

    long countByProprietarioId(UUID proprietarioId);

    /** Remove as notificações do proprietário na exclusão da conta (LGPD). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Notificacao n where n.proprietario.id = :proprietarioId")
    void deleteAllByProprietarioId(@Param("proprietarioId") UUID proprietarioId);
}
