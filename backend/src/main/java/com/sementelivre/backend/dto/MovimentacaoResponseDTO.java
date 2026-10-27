package com.sementelivre.backend.dto;

import com.sementelivre.backend.entity.enums.OrigemMovimentacao;
import com.sementelivre.backend.entity.enums.TipoMovimentacao;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Uma linha do histórico de estoque.
 *
 * {@code aumento} vem pronto do backend porque a UI precisa pintar a linha de
 * verde ou vermelho, e decidir isso no cliente exigiria recalcular a direção
 * a partir de dois números.
 */
public record MovimentacaoResponseDTO(
        UUID id,
        UUID estoqueId,
        TipoMovimentacao tipo,
        OrigemMovimentacao origem,
        Double quantidade,
        Double saldoAnterior,
        Double saldoPosterior,
        boolean aumento,
        String descricao,
        String usuarioNome,
        LocalDateTime dataMovimentacao
) {
}
