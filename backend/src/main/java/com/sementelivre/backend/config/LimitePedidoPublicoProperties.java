package com.sementelivre.backend.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Limites do pedido do site público (POST /catalogo/pedidos). */
@ConfigurationProperties(prefix = "app.pedido-publico.limite")
public class LimitePedidoPublicoProperties {

    /** Pedidos por e-mail de destino dentro da janela. Protege a caixa de quem recebe a cópia. */
    private int porEmail = 3;
    private Duration janelaEmail = Duration.ofHours(1);

    /** Pedidos por IP do cliente dentro da janela. */
    private int porIp = 10;
    private Duration janelaIp = Duration.ofMinutes(10);

    /**
     * IPs/redes dos proxies (o servidor do front-site) cujo X-Forwarded-For é
     * aceito. Requisição vinda de outro lugar usa o IP da conexão, então um
     * X-Forwarded-For forjado direto no backend não troca o IP.
     */
    private List<String> proxiesConfiaveis = new ArrayList<>(List.of(
            "127.0.0.1/8", "::1", "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16"));

    public int getPorEmail() {
        return porEmail;
    }

    public void setPorEmail(int porEmail) {
        this.porEmail = porEmail;
    }

    public Duration getJanelaEmail() {
        return janelaEmail;
    }

    public void setJanelaEmail(Duration janelaEmail) {
        this.janelaEmail = janelaEmail;
    }

    public int getPorIp() {
        return porIp;
    }

    public void setPorIp(int porIp) {
        this.porIp = porIp;
    }

    public Duration getJanelaIp() {
        return janelaIp;
    }

    public void setJanelaIp(Duration janelaIp) {
        this.janelaIp = janelaIp;
    }

    public List<String> getProxiesConfiaveis() {
        return proxiesConfiaveis;
    }

    public void setProxiesConfiaveis(List<String> proxiesConfiaveis) {
        this.proxiesConfiaveis = proxiesConfiaveis;
    }
}
