package com.sementelivre.backend.service;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import com.sementelivre.backend.config.LimiteAutenticacaoProperties;
import com.sementelivre.backend.config.LimiteAutenticacaoProperties.Regra;
import com.sementelivre.backend.exception.MuitasTentativasException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Limita as rotas abertas de /auth contra força bruta (#200).
 *
 * No login o limite por IP conta toda tentativa; o por e-mail conta só senha
 * errada e zera com o login certo. Assim trocar de IP não dá tentativas novas
 * para a mesma conta, e quem erra a senha algumas vezes não fica preso depois
 * de acertar.
 */
@Service
@EnableConfigurationProperties(LimiteAutenticacaoProperties.class)
public class LimiteAutenticacaoService {

    private final LimiteAutenticacaoProperties limites;
    private final LimitePedidoPublicoService ip;

    // Um limitador por regra: cada um limpa as chaves velhas pela própria janela
    private final LimitadorDeRequisicoes loginPorIp;
    private final LimitadorDeRequisicoes senhaErradaPorEmail;
    private final LimitadorDeRequisicoes refreshPorIp;
    private final LimitadorDeRequisicoes recuperacaoPorIp;
    private final LimitadorDeRequisicoes recuperacaoPorEmail;
    private final LimitadorDeRequisicoes cadastroPorIp;

    @Autowired
    public LimiteAutenticacaoService(LimiteAutenticacaoProperties limites, LimitePedidoPublicoService ip) {
        this(limites, ip, Clock.systemUTC());
    }

    LimiteAutenticacaoService(LimiteAutenticacaoProperties limites, LimitePedidoPublicoService ip, Clock relogio) {
        this.limites = limites;
        this.ip = ip;
        this.loginPorIp = new LimitadorDeRequisicoes(relogio);
        this.senhaErradaPorEmail = new LimitadorDeRequisicoes(relogio);
        this.refreshPorIp = new LimitadorDeRequisicoes(relogio);
        this.recuperacaoPorIp = new LimitadorDeRequisicoes(relogio);
        this.recuperacaoPorEmail = new LimitadorDeRequisicoes(relogio);
        this.cadastroPorIp = new LimitadorDeRequisicoes(relogio);
    }

    /** Antes de conferir a senha. Lança {@link MuitasTentativasException} acima do limite. */
    public void antesDoLogin(HttpServletRequest request, String email) {
        Regra porEmail = limites.getSenhaErradaPorEmail();
        barrar(senhaErradaPorEmail.consultar(chaveEmail(email), porEmail.getLimite(), porEmail.getJanela()),
                "Muitas tentativas com senha errada para este e-mail.");
        barrar(tentar(loginPorIp, ip.ipDoCliente(request), limites.getLoginPorIp()),
                "Muitas tentativas de login deste dispositivo.");
    }

    public void senhaErrada(String email) {
        senhaErradaPorEmail.registrar(chaveEmail(email), limites.getSenhaErradaPorEmail().getJanela());
    }

    public void loginCerto(String email) {
        senhaErradaPorEmail.esquecer(chaveEmail(email));
    }

    public void antesDoRefresh(HttpServletRequest request) {
        barrar(tentar(refreshPorIp, ip.ipDoCliente(request), limites.getRefreshPorIp()),
                "Muitas renovações de sessão deste dispositivo.");
    }

    /** Pedido de recuperação (manda e-mail): por IP e por e-mail de destino. */
    public void antesDaRecuperacao(HttpServletRequest request, String email) {
        barrar(tentar(recuperacaoPorIp, ip.ipDoCliente(request), limites.getRecuperacaoPorIp()),
                "Muitos pedidos de recuperação de senha deste dispositivo.");
        barrar(tentar(recuperacaoPorEmail, chaveEmail(email), limites.getRecuperacaoPorEmail()),
                "Este e-mail já recebeu muitos pedidos de recuperação de senha.");
    }

    /** Redefinição com o token do e-mail: divide o limite por IP com a recuperação. */
    public void antesDaRedefinicao(HttpServletRequest request) {
        barrar(tentar(recuperacaoPorIp, ip.ipDoCliente(request), limites.getRecuperacaoPorIp()),
                "Muitas tentativas de redefinir a senha deste dispositivo.");
    }

    public void antesDoCadastro(HttpServletRequest request) {
        barrar(tentar(cadastroPorIp, ip.ipDoCliente(request), limites.getCadastroPorIp()),
                "Muitos cadastros feitos deste dispositivo.");
    }

    private static Optional<Duration> tentar(LimitadorDeRequisicoes limitador, String chave, Regra regra) {
        return limitador.tentar(chave, regra.getLimite(), regra.getJanela());
    }

    private static String chaveEmail(String email) {
        return email == null ? "" : LimitePedidoPublicoService.normalizarEmail(email);
    }

    private static void barrar(Optional<Duration> espera, String motivo) {
        espera.ifPresent(tempo -> {
            throw new MuitasTentativasException(motivo + " Tente novamente " + quando(tempo) + ".", tempo);
        });
    }

    // "em 12 minutos", "em 1 minuto"
    private static String quando(Duration espera) {
        long minutos = Math.max(1, (espera.toSeconds() + 59) / 60);
        return "em " + minutos + (minutos == 1 ? " minuto" : " minutos");
    }
}
