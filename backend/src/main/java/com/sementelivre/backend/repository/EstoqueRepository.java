package com.sementelivre.backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sementelivre.backend.entity.Estoque;
import com.sementelivre.backend.entity.enums.Disponibilidade;
import com.sementelivre.backend.entity.enums.EspecieGeral;
import com.sementelivre.backend.entity.enums.TipoProduto;

import jakarta.persistence.LockModeType;

public interface EstoqueRepository extends BaseRepository<Estoque, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select e from Estoque e
            where e.proprietario.id = :proprietarioId
              and e.produto.id = :produtoId
            """)
    Optional<Estoque> findParaAtualizacao(
            @Param("proprietarioId") UUID proprietarioId,
            @Param("produtoId") UUID produtoId);

    /**
     * Mesmo lock, buscando pelo id da oferta (pedido do site público). A
     * primeira leitura precisa já travar: se o estoque fosse lido antes sem
     * lock, o Hibernate devolveria essa cópia antiga no findParaAtualizacao e
     * pedidos simultâneos baixariam do mesmo saldo.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Estoque e where e.id = :id")
    Optional<Estoque> findPorIdParaAtualizacao(@Param("id") UUID id);

    @Query("""
            select e from Estoque e
            where e.proprietario.id = :proprietarioId
              and e.produto.id = :produtoId
            """)
    Optional<Estoque> findByProprietarioIdAndProdutoId(
            @Param("proprietarioId") UUID proprietarioId,
            @Param("produtoId") UUID produtoId);

    /**
     * Estoques de um proprietario que podem ser exibidos no site publico
     * (issue #91).
     */
    default List<Estoque> findVisiveisNoSitePublico(UUID proprietarioId) {
        return findByProprietarioIdExcetoDisponibilidade(proprietarioId, Disponibilidade.INDISPONIVEL);
    }

    /**
     * A disponibilidade vai como parametro, e nao como literal no JPQL: com o
     * literal o Hibernate gera o cast para o tipo "disponibilidade" (nome da
     * classe Java), que nao existe no PostgreSQL, e o perfil publico dava 500.
     */
    @Query("""
            select e from Estoque e
            join fetch e.produto p
            where e.proprietario.id = :proprietarioId
              and e.disponibilidade <> :excluida
            order by p.nomePopular
            """)
    List<Estoque> findByProprietarioIdExcetoDisponibilidade(
            @Param("proprietarioId") UUID proprietarioId,
            @Param("excluida") Disponibilidade excluida);

    /**
     * Catalogo publico - issue #93.
     *
     * As disponibilidades permitidas sao recebidas como parametro para evitar
     * que o Hibernate gere casts PostgreSQL usando o nome da classe Java
     * Disponibilidade em vez do tipo disponibilidade_produto_enum.
     */
    @Query("""
            select e from Estoque e
            join e.produto p
            left join p.comunidadeOrigem c
            left join c.logradouro l
            where e.disponibilidade in :disponibilidadesPublicas
              and (:nomePopular = ''
                   or lower(p.nomePopular) like concat('%', :nomePopular, '%'))
              and p.tipo in :tipos
              and p.especie in :especies
              and e.disponibilidade in :disponibilidadesFiltro
              and (:comunidade = ''
                   or lower(c.nome) like concat('%', :comunidade, '%'))
              and (:municipio = ''
                   or lower(l.municipio) like concat('%', :municipio, '%'))
            """)
    Page<Estoque> buscarCatalogoPublico(
            @Param("disponibilidadesPublicas")
            List<Disponibilidade> disponibilidadesPublicas,
            @Param("nomePopular") String nomePopular,
            @Param("tipos") List<TipoProduto> tipos,
            @Param("especies") List<EspecieGeral> especies,
            @Param("disponibilidadesFiltro") List<Disponibilidade> disponibilidadesFiltro,
            @Param("comunidade") String comunidade,
            @Param("municipio") String municipio,
            Pageable pageable);

    @Query("""
            select e from Estoque e
            join fetch e.produto p
            left join fetch p.comunidadeOrigem c
            left join fetch c.logradouro l
            where p.id = :produtoId
              and e.disponibilidade in :disponibilidadesPublicas
            """)
    List<Estoque> buscarProdutosPublicosPorId(
            @Param("produtoId") UUID produtoId,
            @Param("disponibilidadesPublicas")
            List<Disponibilidade> disponibilidadesPublicas);

    long countByProprietarioId(UUID proprietarioId);
    /**
     * Verifica se o Proprietario tem algum Estoque vinculado, usado para
     * bloquear exclusão de Propriedade com dependências (issue #63).
     */
    boolean existsByProprietarioId(UUID proprietarioId);

    /**
     * Remove os estoques do proprietário na exclusão da conta (LGPD). O product
     * em si é catálogo compartilhado e continua existindo.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Estoque e where e.proprietario.id = :proprietarioId")
    void deleteAllByProprietarioId(@Param("proprietarioId") UUID proprietarioId);
}