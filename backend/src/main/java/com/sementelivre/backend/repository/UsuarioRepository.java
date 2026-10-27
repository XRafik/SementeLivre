package com.sementelivre.backend.repository;

import com.sementelivre.backend.entity.Usuario;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UsuarioRepository extends BaseRepository<Usuario, UUID> {
    Optional<Usuario> findByPessoaEmail(String email);

    // Login e recuperacao de senha: o usuario pode digitar o e-mail com outra caixa.
    Optional<Usuario> findByPessoaEmailIgnoreCase(String email);

    // UsuarioResponseDTO le o endereco da pessoa (LAZY) no controller, fora da
    // transacao: sem isto, em producao (open-in-view: false) a resposta dava 500.
    // "roles" entra junto porque o grafo deixa LAZY o que nao estiver nele.
    @Override
    @EntityGraph(attributePaths = { "pessoa.logradouro", "roles" })
    Optional<Usuario> findById(UUID id);

    @Override
    @EntityGraph(attributePaths = { "pessoa.logradouro", "roles" })
    List<Usuario> findAll();
}
