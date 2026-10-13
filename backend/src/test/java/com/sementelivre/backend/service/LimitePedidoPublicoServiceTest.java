package com.sementelivre.backend.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import com.sementelivre.backend.config.LimitePedidoPublicoProperties;
import com.sementelivre.backend.exception.LimiteDePedidosExcedidoException;

class LimitePedidoPublicoServiceTest {

    private final LimitadorDeRequisicoesTest.RelogioAjustavel relogio = new LimitadorDeRequisicoesTest.RelogioAjustavel();
    private LimitePedidoPublicoService service;

    @BeforeEach
    void setUp() {
        LimitePedidoPublicoProperties limites = new LimitePedidoPublicoProperties();
        limites.setPorEmail(3);
        limites.setJanelaEmail(Duration.ofHours(1));
        limites.setPorIp(5);
        limites.setJanelaIp(Duration.ofMinutes(10));
        service = new LimitePedidoPublicoService(limites, relogio);
    }

    // E-MAIL

    @Test
    void variacoesDoMesmoEmailContamJuntas() {
        service.verificar(direto("198.51.100.1"), "Fulano.Silva@Gmail.com");
        service.verificar(direto("198.51.100.2"), "fulanosilva+loja@gmail.com");
        service.verificar(direto("198.51.100.3"), " f.u.l.a.n.o.s.i.l.v.a@googlemail.com ");

        LimiteDePedidosExcedidoException e = assertThrows(LimiteDePedidosExcedidoException.class,
                () -> service.verificar(direto("198.51.100.4"), "fulanosilva@gmail.com"));

        assertTrue(e.getMessage().startsWith("Este e-mail já recebeu muitos pedidos"), e.getMessage());
        assertEquals(Duration.ofHours(1), e.getTentarDeNovoEm());
    }

    @Test
    void trocarDeIpNaoBurlaOLimitePorEmail() {
        for (int i = 1; i <= 3; i++) {
            service.verificar(direto("198.51.100." + i), "vitima@exemplo.com");
        }
        assertThrows(LimiteDePedidosExcedidoException.class,
                () -> service.verificar(direto("203.0.113.99"), "vitima@exemplo.com"));
    }

    @Test
    void emailLiberaDepoisDaJanela() {
        for (int i = 0; i < 3; i++) {
            service.verificar(direto("198.51.100." + i), "ana@exemplo.com");
        }
        relogio.avancar(Duration.ofHours(1));

        assertDoesNotThrow(() -> service.verificar(direto("198.51.100.9"), "ana@exemplo.com"));
    }

    @Test
    void normalizaEmail() {
        assertEquals("fulanosilva@gmail.com", LimitePedidoPublicoService.normalizarEmail(" F.u.l.a.n.o.Silva+x@GoogleMail.com"));
        // fora do Gmail o ponto faz parte do endereço
        assertEquals("ana.souza@exemplo.com", LimitePedidoPublicoService.normalizarEmail("Ana.Souza+promo@Exemplo.com"));
    }

    // IP

    @Test
    void limitaPorIp() {
        for (int i = 0; i < 5; i++) {
            service.verificar(direto("203.0.113.7"), "pessoa" + i + "@exemplo.com");
        }
        LimiteDePedidosExcedidoException e = assertThrows(LimiteDePedidosExcedidoException.class,
                () -> service.verificar(direto("203.0.113.7"), "outra@exemplo.com"));

        assertTrue(e.getMessage().startsWith("Muitos pedidos enviados deste dispositivo"), e.getMessage());
        assertTrue(e.getMessage().endsWith("em 10 minutos."), e.getMessage());
    }

    @Test
    void ipBloqueadoNaoGastaACotaDoEmail() {
        for (int i = 0; i < 5; i++) {
            service.verificar(direto("203.0.113.7"), "pessoa" + i + "@exemplo.com");
        }
        for (int i = 0; i < 4; i++) {
            assertThrows(LimiteDePedidosExcedidoException.class,
                    () -> service.verificar(direto("203.0.113.7"), "ana@exemplo.com"));
        }

        assertDoesNotThrow(() -> service.verificar(direto("198.51.100.1"), "ana@exemplo.com"));
    }

    @Test
    void usaOXForwardedForQuandoVemDoFrontSite() {
        assertEquals("198.51.100.20", service.ipDoCliente(viaProxy("127.0.0.1", "198.51.100.20")));
        assertEquals("198.51.100.20", service.ipDoCliente(viaProxy("172.18.0.1", "198.51.100.20")));
        // o primeiro da direita que não é proxy: o que o cliente pôs à esquerda não vale
        assertEquals("198.51.100.20", service.ipDoCliente(viaProxy("127.0.0.1", "1.2.3.4, 198.51.100.20, 10.0.0.5")));
    }

    @Test
    void ignoraXForwardedForVindoDeForaDosProxies() {
        // chamada direta ao backend com cabeçalho forjado
        assertEquals("203.0.113.50", service.ipDoCliente(viaProxy("203.0.113.50", "198.51.100.20")));
    }

    @Test
    void xForwardedForInvalidoNaoQuebra() {
        assertEquals("unknown", service.ipDoCliente(viaProxy("127.0.0.1", "unknown")));
        assertEquals("127.0.0.1", service.ipDoCliente(viaProxy("127.0.0.1", " ")));
    }

    private static MockHttpServletRequest direto(String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(ip);
        return request;
    }

    private static MockHttpServletRequest viaProxy(String proxy, String encaminhado) {
        MockHttpServletRequest request = direto(proxy);
        request.addHeader("X-Forwarded-For", encaminhado);
        return request;
    }
}
