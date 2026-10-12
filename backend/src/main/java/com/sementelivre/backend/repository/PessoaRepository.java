package com.sementelivre.backend.repository;

import com.sementelivre.backend.entity.Pessoa;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PessoaRepository extends BaseRepository<Pessoa, UUID> {
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByDocumento(String documento);
}
