package com.sementelivre.backend.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.sementelivre.backend.entity.enums.StatusPedido;
import com.sementelivre.backend.entity.enums.TipoPedido;

/** O que o site mostra depois de registrar o pedido. Não expõe ids de produtor. */
public record PedidoPublicoResponseDTO(
        UUID id,
        TipoPedido tipoPedido,
        StatusPedido status,
        LocalDateTime dataPedido,
        String produto,
        Double quantidade
) {
}
