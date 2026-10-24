package com.sementelivre.backend.util;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.sementelivre.backend.entity.Usuario;
import com.sementelivre.backend.exception.RecursoNaoEncontradoException;

/**
 * Pega o id de quem esta logado, a partir do token.
 *
 * <p>O id do Usuario e o mesmo do Proprietario, entao da para comparar
 * direto com o dono da notificacao ou do relatorio.</p>
 */
public final class UsuarioLogado {

    private UsuarioLogado() {
    }

    public static UUID id() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !(auth.getPrincipal() instanceof Usuario usuario)) {
            throw new RecursoNaoEncontradoException("Nenhuma sessão autenticada.");
        }

        return usuario.getId();
    }
}
