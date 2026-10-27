package com.sementelivre.backend.entity.enums;

import java.util.Set;

/**
 * Ciclo de vida do pedido. Cada constante declara para quais estados pode
 * transicionar, de modo que a regra de transicao fique junto do proprio enum
 * em vez de espalhada pelo service.
 *
 * O estoque e reservado (baixado) quando o pedido e registrado, ja PENDENTE:
 *
 * PENDENTE --> CONFIRMADO (so muda o status, o estoque ja foi baixado)
 * PENDENTE --> CANCELADO  (restaura o estoque reservado no registro)
 * CONFIRMADO --> CANCELADO (restaura o estoque reservado no registro)
 * CANCELADO --> (estado final; por isso a restauracao acontece uma vez so)
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
