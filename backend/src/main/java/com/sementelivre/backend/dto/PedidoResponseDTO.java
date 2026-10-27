package com.sementelivre.backend.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.sementelivre.backend.entity.enums.StatusPedido;
import com.sementelivre.backend.entity.enums.TipoPedido;

public record PedidoResponseDTO(

        UUID id,
        TipoPedido tipoPedido,
        String mensagemOpcional,
        LocalDateTime dataPedido,
        StatusPedido status,
        UUID usuarioSolicitanteId,
        UUID proprietarioRecebedorId,
        List<ItemPedidoResponseDTO> itens,
        CompradorDTO comprador

) {
}
