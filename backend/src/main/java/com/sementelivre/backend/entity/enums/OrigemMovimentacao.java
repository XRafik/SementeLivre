package com.sementelivre.backend.entity.enums;

/**
 * De onde veio a movimentação de estoque. Separa o que o produtor fez na mão
 * do que o sistema gerou sozinho, para o relatório de auditoria não misturar
 * as duas coisas.
 */
public enum OrigemMovimentacao {
    /** Saldo inicial criado no cadastro do estoque. */
    CADASTRO,
    /** Correção de quantidade feita pelo produtor na tela de estoque. */
    AJUSTE_MANUAL,
    /** Reserva/entrega gerada por um pedido. */
    PEDIDO,
    /** Devolução que devolveu quantidade ao estoque. */
    DEVOLUCAO,
    /** Estoque removido junto com o produto. */
    EXCLUSAO_PRODUTO
}
