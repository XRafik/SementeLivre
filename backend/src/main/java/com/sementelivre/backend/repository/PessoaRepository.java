package com.sementelivre.backend.repository;

import com.sementelivre.backend.entity.Pessoa;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PessoaRepository extends BaseRepository<Pessoa, UUID> {
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByDocumento(String documento);

    // O endereco (LAZY) vai em toda resposta de pessoa e e montado no controller,
    // fora da transacao. Em producao (open-in-view: false) isso dava
    // LazyInitializationException e 500; aqui ele ja vem junto.
    @Override
    @EntityGraph(attributePaths = "logradouro")
    Optional<Pessoa> findById(UUID id);

    @Override
    @EntityGraph(attributePaths = "logradouro")
    List<Pessoa> findAll();
}
