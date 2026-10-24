package com.sementelivre.backend.util;

import java.util.ArrayList;
import java.util.List;

import com.sementelivre.backend.entity.Itens;
import com.sementelivre.backend.entity.Pedido;

/**
 * Monta o texto das notificacoes de pedido com os detalhes do pedido (CDU-26).
 *
 * <p>Exemplo: "Pedido de TROCA confirmado. Solicitante: Maria. Itens: 2 x Feijao Crioulo."</p>
 */
public final class MensagemPedido {

    private MensagemPedido() {
    }

    public static String montar(Pedido pedido, String evento) {

        StringBuilder mensagem = new StringBuilder();

        if (pedido.getTipoPedido() != null) {
            mensagem.append("Pedido de ").append(pedido.getTipoPedido()).append(" ").append(evento).append(".");
        } else {
            mensagem.append("Pedido ").append(evento).append(".");
        }

        if (pedido.getUsuarioSolicitante() != null) {
            mensagem.append(" Solicitante: ").append(pedido.getUsuarioSolicitante().getNome()).append(".");
        }

        if (pedido.getComprador() != null) {
            mensagem.append(" Comprador: ").append(pedido.getComprador().getNome()).append(".");
        }

        String itens = montarListaDeItens(pedido);

        if (!itens.isEmpty()) {
            mensagem.append(" Itens: ").append(itens).append(".");
        }

        return mensagem.toString();
    }

    // Junta os itens em um texto so: "2 x Feijao Crioulo, 1 x Milho"
    private static String montarListaDeItens(Pedido pedido) {

        if (pedido.getItens() == null) {
            return "";
        }

        List<String> itens = new ArrayList<>();

        for (Itens item : pedido.getItens()) {
            String nomeProduto = "produto";

            if (item.getProduto() != null && item.getProduto().getNomePopular() != null) {
                nomeProduto = item.getProduto().getNomePopular();
            }

            itens.add(formatarQuantidade(item.getQuantidade()) + " x " + nomeProduto);
        }

        return String.join(", ", itens);
    }

    // A quantidade e Double: mostra 2 em vez de 2.0 quando nao tem casa decimal
    private static String formatarQuantidade(Double quantidade) {

        if (quantidade == null) {
            return "0";
        }

        if (quantidade == Math.floor(quantidade)) {
            return String.valueOf(quantidade.longValue());
        }

        return String.valueOf(quantidade);
    }
}
