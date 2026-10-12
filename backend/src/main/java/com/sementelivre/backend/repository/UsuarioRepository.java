package com.sementelivre.backend.repository;

import com.sementelivre.backend.entity.Usuario;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UsuarioRepository extends BaseRepository<Usuario, UUID> {
    Optional<Usuario> findByPessoaEmail(String email);

    // Login e recuperacao de senha: o usuario pode digitar o e-mail com outra caixa.
    Optional<Usuario> findByPessoaEmailIgnoreCase(String email);
}
