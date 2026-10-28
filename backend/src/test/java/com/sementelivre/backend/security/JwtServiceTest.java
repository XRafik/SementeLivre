package com.sementelivre.backend.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.sementelivre.backend.entity.Proprietario;
import com.sementelivre.backend.entity.Role;
import com.sementelivre.backend.entity.Usuario;
import com.sementelivre.backend.entity.enums.PerfilEnum;

/**
 * A chave do JWT só vem de JWT_SECRET (#198). Antes o application-dev.yml
 * trazia uma chave fixa, que está no git, como valor padrão.
 */
class JwtServiceTest {

    private static final String CHAVE = "chaveDoTesteDoJwtServiceComMaisDe256Bits0123456789";

    // A chave que ficava no application-dev.yml
    private static final String CHAVE_ANTIGA_DO_REPOSITORIO =
            "sementeLivreChaveSecretaSuperSeguraComMaisDe256Bits1234567890!";

    private static Usuario usuario() {
        Proprietario proprietario = new Proprietario();
        proprietario.setNome("Produtor Teste");
        proprietario.setEmail("produtor@teste.com");

        Usuario usuario = new Usuario();
        usuario.setId(UUID.randomUUID());
        usuario.setPessoa(proprietario);
        usuario.getRoles().add(Role.builder().nome(PerfilEnum.ROLE_ADMIN).build());
        return usuario;
    }

    @Test
    void semJwtSecretForaDoDevNaoDeveSubir() {
        assertThrows(IllegalStateException.class, () -> new JwtService("", 1800000, false));
        assertThrows(IllegalStateException.class, () -> new JwtService(null, 1800000, false));
    }

    @Test
    void semJwtSecretNoDevDeveSortearUmaChavePorSubida() {
        JwtService primeiraSubida = new JwtService("", 1800000, true);
        JwtService segundaSubida = new JwtService("", 1800000, true);

        String token = primeiraSubida.gerarToken(usuario());

        assertTrue(primeiraSubida.tokenValido(token));
        assertFalse(segundaSubida.tokenValido(token));
    }

    @Test
    void tokenAssinadoComAChaveAntigaDoRepositorioNaoDeveValer() {
        String forjado = new JwtService(CHAVE_ANTIGA_DO_REPOSITORIO, 1800000, false).gerarToken(usuario());

        assertFalse(new JwtService(CHAVE, 1800000, false).tokenValido(forjado));
        assertFalse(new JwtService("", 1800000, true).tokenValido(forjado));
    }

    @Test
    void comJwtSecretDeveAssinarEValidarComEla() {
        Usuario usuario = usuario();
        String token = new JwtService(CHAVE, 1800000, false).gerarToken(usuario);

        JwtService outraInstanciaMesmaChave = new JwtService(CHAVE, 1800000, false);
        assertTrue(outraInstanciaMesmaChave.tokenValido(token));
        assertEquals("produtor@teste.com", outraInstanciaMesmaChave.extrairEmail(token));
    }
}
