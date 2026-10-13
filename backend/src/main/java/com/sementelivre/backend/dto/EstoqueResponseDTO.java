package com.sementelivre.backend.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.sementelivre.backend.entity.enums.Disponibilidade;
import com.sementelivre.backend.entity.enums.Pesagem;
import com.sementelivre.backend.entity.enums.TipoMovimentacao;

public record EstoqueResponseDTO(

        UUID id,
        UUID proprietarioId,
        UUID produtoId,
        String descricao,
        Double preco,
        Double quantidade,
        Pesagem tipoPesagem,
        Disponibilidade disponibilidade,
        TipoMovimentacao tipoMovimentacao,
        LocalDateTime dataMovimentacao,
        LocalDateTime dataUltimaAtualizacao,
        boolean aceitaNegociacao
) {
}
