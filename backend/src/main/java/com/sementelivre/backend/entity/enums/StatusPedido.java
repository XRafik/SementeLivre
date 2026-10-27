package com.sementelivre.backend.entity.enums;

import java.util.Set;

/**
 * Ciclo de vida do pedido. Cada constante declara para quais estados pode
 * transicionar, de modo que a regra de transicao fique junto do proprio enum
 * em vez de espalhada pelo service.
 *
 * O pedido PENDENTE esta na fila e nao mexe no estoque (#183):
 *
 * PENDENTE --> CONFIRMADO (baixa o estoque; recusa os pendentes que nao cabem mais)
 * PENDENTE --> CANCELADO  (cancelado pelo produtor ou recusado pela fila; nada a devolver)
 * CONFIRMADO --> CANCELADO (devolve o que foi baixado na confirmacao)
 * CANCELADO --> (estado final; por isso a devolucao acontece uma vez so)
 */
public enum StatusPedido {

    PENDENTE,
    CONFIRMADO,
    CANCELADO;

    public boolean podeTransicionarPara(StatusPedido destino) {
        return proximosEstados().contains(destino);
    }

    public Set<StatusPedido> proximosEstados() {
        return switch (this) {
            case PENDENTE -> Set.of(CONFIRMADO, CANCELADO);
            case CONFIRMADO -> Set.of(CANCELADO);
            case CANCELADO -> Set.of();
        };
    }
}
