package com.sementelivre.backend.dto;

import java.util.UUID;

import com.sementelivre.backend.entity.enums.Disponibilidade;
import com.sementelivre.backend.entity.enums.Pesagem;

public record CatalogoOfertaResponseDTO(

        UUID estoqueId,
        String descricao,
        Double preco,
        Double quantidade,
        Pesagem tipoPesagem,
        Disponibilidade disponibilidade,
        boolean aceitaNegociacao
) {
}