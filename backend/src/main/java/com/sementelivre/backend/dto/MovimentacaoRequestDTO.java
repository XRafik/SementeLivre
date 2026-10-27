package com.sementelivre.backend.dto;

import com.sementelivre.backend.entity.enums.TipoMovimentacao;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Lançamento avulso contra um estoque existente.
 *
 * O tipo define o sentido: ENTRADA soma, os tipos SAIDA_* subtraem. Correção
 * de saldo não passa por aqui de propósito — esse caminho é o PUT do estoque,
 * que guarda o saldo anterior e o novo, e não sofre de ambiguidade de sinal.
 */
public record MovimentacaoRequestDTO(
        @NotNull(message = "O tipo da movimentação é obrigatório")
        TipoMovimentacao tipo,

        @NotNull(message = "A quantidade é obrigatória")
        @Positive(message = "A quantidade deve ser maior que zero")
        Double quantidade,

        String descricao
) {
}
