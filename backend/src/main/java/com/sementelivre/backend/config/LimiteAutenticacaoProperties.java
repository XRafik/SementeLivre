package com.sementelivre.backend.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Limites das rotas abertas de /auth (#200): login, refresh, recuperação de
 * senha e cadastro. Sem eles dava para testar senhas sem parar.
 */
@ConfigurationProperties(prefix = "app.auth.limite")
public class LimiteAutenticacaoProperties {

    /** Tentativas de login por IP. */
    private Regra loginPorIp = new Regra(30, Duration.ofMinutes(15));

    /** Senhas erradas seguidas por e-mail; o login certo zera a conta. */
    private Regra senhaErradaPorEmail = new Regra(10, Duration.ofMinutes(15));

    /** Renovações de token por IP (o app renova a cada 30 minutos e ao abrir). */
    private Regra refreshPorIp = new Regra(120, Duration.ofMinutes(10));

    /** Pedidos de recuperação e redefinições de senha por IP. */
    private Regra recuperacaoPorIp = new Regra(10, Duration.ofHours(1));

    /** Pedidos de recuperação por e-mail: cada um manda um e-mail. */
    private Regra recuperacaoPorEmail = new Regra(3, Duration.ofHours(1));

    /** Cadastros de conta por IP. */
    private Regra cadastroPorIp = new Regra(20, Duration.ofHours(1));

    public static class Regra {

        private int limite;
        private Duration janela;

        public Regra() {
        }

        public Regra(int limite, Duration janela) {
            this.limite = limite;
            this.janela = janela;
        }

        public int getLimite() {
            return limite;
        }

        public void setLimite(int limite) {
            this.limite = limite;
        }

        public Duration getJanela() {
            return janela;
        }

        public void setJanela(Duration janela) {
            this.janela = janela;
        }
    }

    public Regra getLoginPorIp() {
        return loginPorIp;
    }

    public void setLoginPorIp(Regra loginPorIp) {
        this.loginPorIp = loginPorIp;
    }

    public Regra getSenhaErradaPorEmail() {
        return senhaErradaPorEmail;
    }

    public void setSenhaErradaPorEmail(Regra senhaErradaPorEmail) {
        this.senhaErradaPorEmail = senhaErradaPorEmail;
    }

    public Regra getRefreshPorIp() {
        return refreshPorIp;
    }

    public void setRefreshPorIp(Regra refreshPorIp) {
        this.refreshPorIp = refreshPorIp;
    }

    public Regra getRecuperacaoPorIp() {
        return recuperacaoPorIp;
    }

    public void setRecuperacaoPorIp(Regra recuperacaoPorIp) {
        this.recuperacaoPorIp = recuperacaoPorIp;
    }

    public Regra getRecuperacaoPorEmail() {
        return recuperacaoPorEmail;
    }

    public void setRecuperacaoPorEmail(Regra recuperacaoPorEmail) {
        this.recuperacaoPorEmail = recuperacaoPorEmail;
    }

    public Regra getCadastroPorIp() {
        return cadastroPorIp;
    }

    public void setCadastroPorIp(Regra cadastroPorIp) {
        this.cadastroPorIp = cadastroPorIp;
    }
}
