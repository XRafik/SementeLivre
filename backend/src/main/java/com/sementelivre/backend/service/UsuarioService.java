package com.sementelivre.backend.service;

import com.sementelivre.backend.entity.Pessoa;
import com.sementelivre.backend.entity.Role;
import com.sementelivre.backend.entity.Usuario;
import com.sementelivre.backend.entity.enums.PerfilEnum;
import com.sementelivre.backend.exception.ResourceNotFoundException;
import com.sementelivre.backend.repository.RefreshTokenRepository;
import com.sementelivre.backend.repository.RoleRepository;
import com.sementelivre.backend.repository.TokenRecuperacaoSenhaRepository;
import com.sementelivre.backend.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Contas de login. Um Usuario sempre pertence a uma Pessoa ja cadastrada
 * (Admin ou Proprietario) e usa o mesmo id dela.
 */
@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenRecuperacaoSenhaRepository tokenRecuperacaoSenhaRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository usuarioRepository, RoleRepository roleRepository,
                          RefreshTokenRepository refreshTokenRepository,
                          TokenRecuperacaoSenhaRepository tokenRecuperacaoSenhaRepository,
                          PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.roleRepository = roleRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.tokenRecuperacaoSenhaRepository = tokenRecuperacaoSenhaRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public Usuario criarConta(Pessoa pessoa, String senha, PerfilEnum perfil) {
        Role role = roleRepository.findByNome(perfil)
                .orElseThrow(() -> new IllegalStateException("Role " + perfil + " não encontrada."));

        Usuario usuario = new Usuario();
        usuario.setPessoa(pessoa);
        usuario.setSenhaHash(passwordEncoder.encode(senha));
        usuario.getRoles().add(role);

        return usuarioRepository.save(usuario);
    }

    @Transactional(readOnly = true)
    public List<Usuario> listarTodos() {
        return usuarioRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Usuario buscarPorId(UUID id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado com o ID: " + id));
    }

    /** Remove a conta de login; a pessoa (Admin/Proprietario) continua cadastrada. */
    @Transactional
    public void excluir(UUID id) {
        remover(buscarPorId(id));
    }

    /** Usado ao excluir uma pessoa: remove a conta dela, se existir. */
    @Transactional
    public void excluirContaDaPessoa(UUID pessoaId) {
        usuarioRepository.findById(pessoaId).ifPresent(this::remover);
    }

    // Os tokens referenciam usuario_t com ON DELETE RESTRICT, entao saem antes da conta.
    // A associacao ManyToMany nao tem cascade: sem limpar o conjunto antes, os
    // registros de usuario_role_t sobrariam e a exclusao bateria em 409.
    private void remover(Usuario usuario) {
        usuario.getRoles().clear();
        usuarioRepository.flush();
        refreshTokenRepository.deleteByUsuario(usuario);
        tokenRecuperacaoSenhaRepository.deleteByUsuario(usuario);
        usuarioRepository.delete(usuario);
    }
}
