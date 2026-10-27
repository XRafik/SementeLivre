package com.sementelivre.backend.dto;

import java.util.UUID;

public record ItemPedidoResponseDTO(

        UUID id,
        UUID produtoId,
        String nomeProduto,
        Double quantidade,
        Double precoUnitario

) {
}
