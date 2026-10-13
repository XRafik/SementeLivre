package com.sementelivre.backend.repository;

import java.util.UUID;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;

import com.sementelivre.backend.entity.Produto;

public interface ProdutoRepository extends BaseRepository<Produto, UUID> {
    @Override
    @EntityGraph(attributePaths = {"comunidadeOrigem"})
    List<Produto> findAll();
}