package com.sementelivre.backend.dto;

import java.time.LocalDate;
import java.util.UUID;

import com.sementelivre.backend.entity.enums.StatusPedido;
import com.sementelivre.backend.entity.enums.TipoPedido;

/**
 * Filtros do historico de pedidos (issue #102). Todos opcionais; campo nulo
 * nao filtra. O periodo e inclusivo nas duas pontas.
 */
public record PedidoFiltroDTO(

        LocalDate dataInicio,
        LocalDate dataFim,
        TipoPedido tipoPedido,
        UUID produtoId,
        StatusPedido status

) {

    public static PedidoFiltroDTO vazio() {
        return new PedidoFiltroDTO(null, null, null, null, null);
    }
}
