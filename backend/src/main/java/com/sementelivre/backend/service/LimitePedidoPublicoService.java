package com.sementelivre.backend.service;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Service;

import com.sementelivre.backend.config.LimitePedidoPublicoProperties;
import com.sementelivre.backend.exception.LimiteDePedidosExcedidoException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Limita o pedido do site público, que é aberto (sem login) e manda e-mail
 * para o endereço informado.
 *
 * O limite por e-mail de destino é o que protege a caixa de alguém de ser
 * inundada de cópias: ele não depende do IP, que pode ser trocado. O limite
 * por IP segura volume de um mesmo cliente.
 */
@Service
@EnableConfigurationProperties(LimitePedidoPublicoProperties.class)
public class LimitePedidoPublicoService {

    private final LimitePedidoPublicoProperties limites;
    private final LimitadorDeRequisicoes limitador;
    private final List<IpAddressMatcher> proxiesConfiaveis;

    @Autowired
    public LimitePedidoPublicoService(LimitePedidoPublicoProperties limites) {
        this(limites, Clock.systemUTC());
    }

    LimitePedidoPublicoService(LimitePedidoPublicoProperties limites, Clock relogio) {
        this.limites = limites;
        this.limitador = new LimitadorDeRequisicoes(relogio);
        this.proxiesConfiaveis = limites.getProxiesConfiaveis().stream().map(IpAddressMatcher::new).toList();
    }

    /** Lança {@link LimiteDePedidosExcedidoException} quando o pedido passa de algum limite. */
    public void verificar(HttpServletRequest request, String emailDestino) {

        limitador.tentar("ip:" + ipDoCliente(request), limites.getPorIp(), limites.getJanelaIp())
                .ifPresent(espera -> {
                    throw new LimiteDePedidosExcedidoException(
                            "Muitos pedidos enviados deste dispositivo. Tente novamente " + quando(espera) + ".",
                            espera);
                });

        limitador.tentar("email:" + normalizarEmail(emailDestino), limites.getPorEmail(), limites.getJanelaEmail())
                .ifPresent(espera -> {
                    throw new LimiteDePedidosExcedidoException(
                            "Este e-mail já recebeu muitos pedidos. Tente novamente " + quando(espera) + ".",
                            espera);
                });
    }

    /**
     * IP de quem fez o pedido. O X-Forwarded-For só vale quando a conexão vem
     * de um proxy confiável (o servidor do front-site); nele, o primeiro IP da
     * direita que não é proxy é o do cliente.
     */
    String ipDoCliente(HttpServletRequest request) {

        String conexao = request.getRemoteAddr();
        String encaminhado = request.getHeader("X-Forwarded-For");

        if (encaminhado == null || encaminhado.isBlank() || !confiavel(conexao)) {
            return conexao;
        }

        String[] saltos = encaminhado.split(",");
        for (int i = saltos.length - 1; i >= 0; i--) {
            String ip = saltos[i].trim();
            if (!ip.isEmpty() && !confiavel(ip)) {
                return ip;
            }
        }
        return saltos[0].trim().isEmpty() ? conexao : saltos[0].trim();
    }

    /**
     * Mesma caixa de entrada, mesma chave: ignora maiúsculas, o "+etiqueta"
     * (fulano+1@x.com cai em fulano@x.com) e, no Gmail, os pontos do usuário.
     */
    static String normalizarEmail(String email) {

        String limpo = email.trim().toLowerCase(Locale.ROOT);
        int arroba = limpo.lastIndexOf('@');
        if (arroba < 0) {
            return limpo;
        }

        String usuario = limpo.substring(0, arroba);
        String dominio = limpo.substring(arroba + 1);

        int mais = usuario.indexOf('+');
        if (mais >= 0) {
            usuario = usuario.substring(0, mais);
        }
        if (dominio.equals("gmail.com") || dominio.equals("googlemail.com")) {
            usuario = usuario.replace(".", "");
            dominio = "gmail.com";
        }
        return usuario + "@" + dominio;
    }

    private boolean confiavel(String ip) {
        try {
            return proxiesConfiaveis.stream().anyMatch(rede -> rede.matches(ip));
        } catch (IllegalArgumentException e) {
            // valor que nem é IP (ex.: "unknown") não é proxy confiável
            return false;
        }
    }

    // "em 12 minutos", "em 1 minuto"
    private static String quando(Duration espera) {
        long minutos = Math.max(1, (espera.toSeconds() + 59) / 60);
        return "em " + minutos + (minutos == 1 ? " minuto" : " minutos");
    }
}
