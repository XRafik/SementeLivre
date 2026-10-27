package com.sementelivre.backend.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.sementelivre.backend.entity.Estoque;

/**
 * Consulta de Estoque usada somente pelos relatorios (issue #95).
 *
 * <p>Fica separada do EstoqueRepository, que e do dominio de catalogo (Dev 5),
 * para nao misturar as consultas dos dois dominios.</p>
 */
@Repository
public interface EstoqueRelatorioRepository extends BaseRepository<Estoque, UUID> {

    List<Estoque> findByProprietarioIdOrderByProdutoNomePopular(UUID proprietarioId);
}
