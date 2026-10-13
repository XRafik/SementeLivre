package com.sementelivre.backend.dto;

import java.util.UUID;

import com.sementelivre.backend.entity.enums.Disponibilidade;
import com.sementelivre.backend.entity.enums.EspecieGeral;
import com.sementelivre.backend.entity.enums.FormatoProduto;
import com.sementelivre.backend.entity.enums.Pesagem;
import com.sementelivre.backend.entity.enums.TipoProduto;

public record CatalogoProdutoResponseDTO(

        UUID id,
        String nomePopular,
        String nomeCientifico,
        String historico,
        String urlFoto,
        TipoProduto tipo,
        EspecieGeral especie,
        FormatoProduto formato,
        String familiaBotanica,

        UUID estoqueId,
        String descricao,
        Double preco,
        Double quantidade,
        Pesagem tipoPesagem,
        Disponibilidade disponibilidade,

        UUID comunidadeId,
        String comunidade,
        String municipio,
        String uf,
        boolean aceitaNegociacao
) {
}