package com.sementelivre.backend.dto;

import java.util.UUID;

import com.sementelivre.backend.entity.enums.Disponibilidade;
import com.sementelivre.backend.entity.enums.Pesagem;
import com.sementelivre.backend.entity.enums.TipoMovimentacao;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record EstoqueRequestDTO(

        @NotNull(message = "Proprietário é obrigatório")
        UUID proprietarioId,

        @NotNull(message = "Produto é obrigatório")
        UUID produtoId,

        String descricao,

        @NotNull(message = "Preço é obrigatório")
        @PositiveOrZero(message = "Preço não pode ser negativo")
        Double preco,

        @NotNull(message = "Quantidade é obrigatória")
        @PositiveOrZero(message = "Quantidade não pode ser negativa")
        Double quantidade,

        @NotNull(message = "Tipo de pesagem é obrigatório")
        Pesagem tipoPesagem,

        @NotNull(message = "Disponibilidade é obrigatória")
        Disponibilidade disponibilidade,

        @NotNull(message = "Tipo de movimentação é obrigatório")
        TipoMovimentacao tipoMovimentacao,

        // Opcional: ausente, a oferta nova aceita negociar e a existente mantém o que tinha
        Boolean aceitaNegociacao
) {
    public EstoqueRequestDTO(UUID proprietarioId, UUID produtoId, String descricao, Double preco,
            Double quantidade, Pesagem tipoPesagem, Disponibilidade disponibilidade,
            TipoMovimentacao tipoMovimentacao) {
        this(proprietarioId, produtoId, descricao, preco, quantidade, tipoPesagem, disponibilidade,
                tipoMovimentacao, null);
    }
}