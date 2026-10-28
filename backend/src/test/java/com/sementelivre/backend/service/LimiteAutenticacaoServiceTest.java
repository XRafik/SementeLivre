package com.sementelivre.backend.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import com.sementelivre.backend.config.LimiteAutenticacaoProperties;
import com.sementelivre.backend.config.LimiteAutenticacaoProperties.Regra;
import com.sementelivre.backend.config.LimitePedidoPublicoProperties;
import com.sementelivre.backend.exception.MuitasTentativasException;

class LimiteAutenticacaoServiceTest {

    private final LimitadorDeRequisicoesTest.RelogioAjustavel relogio = new LimitadorDeRequisicoesTest.RelogioAjustavel();
    private LimiteAutenticacaoService service;

    @BeforeEach
    void setUp() {
        LimiteAutenticacaoProperties limites = new LimiteAutenticacaoProperties();
        limites.setLoginPorIp(new Regra(5, Duration.ofMinutes(15)));
        limites.setSenhaErradaPorEmail(new Regra(3, Duration.ofMinutes(15)));
        limites.setRecuperacaoPorIp(new Regra(4, Duration.ofHours(1)));
        limites.setRecuperacaoPorEmail(new Regra(2, Duration.ofHours(1)));
        limites.setCadastroPorIp(new Regra(2, Duration.ofHours(1)));
        limites.setRefreshPorIp(new Regra(2, Duration.ofMinutes(10)));
        service = new LimiteAutenticacaoService(limites,
                new LimitePedidoPublicoService(new LimitePedidoPublicoProperties()), relogio);
    }

    private static MockHttpServletRequest de(String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(ip);
        return request;
    }

    /** Uma tentativa de login com a senha errada. */
    private void errar(String ip, String email) {
        service.antesDoLogin(de(ip), email);
        service.senhaErrada(email);
    }

    @Test
    void senhasErradasDemaisBloqueiamOEmailMesmoTrocandoDeIp() {
        errar("198.51.100.1", "vitima@exemplo.com");
        errar("198.51.100.2", "vitima@exemplo.com");
        errar("198.51.100.3", " Vitima@Exemplo.com");

        MuitasTentativasException e = assertThrows(MuitasTentativasException.class,
                () -> service.antesDoLogin(de("203.0.113.9"), "vitima@exemplo.com"));

        assertTrue(e.getMessage().startsWith("Muitas tentativas com senha errada para este e-mail"), e.getMessage());
        assertEquals(Duration.ofMinutes(15), e.getTentarDeNovoEm());
        // Outra conta segue entrando
        assertDoesNotThrow(() -> service.antesDoLogin(de("203.0.113.9"), "outra@exemplo.com"));
    }

    @Test
    void loginCertoZeraAsSenhasErradas() {
        errar("198.51.100.1", "ana@exemplo.com");
        errar("198.51.100.1", "ana@exemplo.com");
        service.antesDoLogin(de("198.51.100.1"), "ana@exemplo.com");
        service.loginCerto("ana@exemplo.com");

        errar("198.51.100.2", "ana@exemplo.com");
        errar("198.51.100.2", "ana@exemplo.com");
        assertDoesNotThrow(() -> service.antesDoLogin(de("198.51.100.3"), "ana@exemplo.com"));
    }

    @Test
    void emailLiberaDepoisDaJanela() {
        for (int i = 0; i < 3; i++) {
            errar("198.51.100." + i, "ana@exemplo.com");
        }
        relogio.avancar(Duration.ofMinutes(15));
        assertDoesNotThrow(() -> service.antesDoLogin(de("198.51.100.9"), "ana@exemplo.com"));
    }

    @Test
    void ipComTentativasDemaisEBloqueadoEmQualquerEmail() {
        for (int i = 0; i < 5; i++) {
            service.antesDoLogin(de("198.51.100.7"), "conta" + i + "@exemplo.com");
        }

        MuitasTentativasException e = assertThrows(MuitasTentativasException.class,
                () -> service.antesDoLogin(de("198.51.100.7"), "nova@exemplo.com"));
        assertTrue(e.getMessage().startsWith("Muitas tentativas de login deste dispositivo"), e.getMessage());
        assertDoesNotThrow(() -> service.antesDoLogin(de("198.51.100.8"), "nova@exemplo.com"));
    }

    @Test
    void recuperacaoLimitadaPorEmailEPorIp() {
        service.antesDaRecuperacao(de("198.51.100.1"), "ana@exemplo.com");
        service.antesDaRecuperacao(de("198.51.100.2"), "ana@exemplo.com");
        assertThrows(MuitasTentativasException.class,
                () -> service.antesDaRecuperacao(de("198.51.100.3"), "ana@exemplo.com"));

        // A redefinição divide o limite por IP com a recuperação
        service.antesDaRecuperacao(de("198.51.100.4"), "a@exemplo.com");
        service.antesDaRedefinicao(de("198.51.100.4"));
        service.antesDaRedefinicao(de("198.51.100.4"));
        service.antesDaRedefinicao(de("198.51.100.4"));
        assertThrows(MuitasTentativasException.class, () -> service.antesDaRedefinicao(de("198.51.100.4")));
    }

    @Test
    void cadastroERefreshLimitadosPorIp() {
        service.antesDoCadastro(de("198.51.100.1"));
        service.antesDoCadastro(de("198.51.100.1"));
        assertThrows(MuitasTentativasException.class, () -> service.antesDoCadastro(de("198.51.100.1")));

        service.antesDoRefresh(de("198.51.100.1"));
        service.antesDoRefresh(de("198.51.100.1"));
        assertThrows(MuitasTentativasException.class, () -> service.antesDoRefresh(de("198.51.100.1")));
    }
}
