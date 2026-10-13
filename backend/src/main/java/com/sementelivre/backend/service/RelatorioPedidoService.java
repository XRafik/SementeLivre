package com.sementelivre.backend.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sementelivre.backend.entity.Itens;
import com.sementelivre.backend.entity.Pedido;
import com.sementelivre.backend.entity.enums.StatusPedido;
import com.sementelivre.backend.entity.enums.TipoPedido;
import com.sementelivre.backend.repository.PedidoRepository;

/**
 * Monta os dados do relatorio de pedidos recebidos (RF-07, issue #95).
 *
 * <p>Uma linha por pedido, com os itens resumidos em uma coluna so.</p>
 */
@Service
public class RelatorioPedidoService {

    public static final String TITULO = "Relatorio de Pedidos Recebidos";

    public static final String[] CABECALHOS = {
            "Data", "Tipo", "Status", "Solicitante", "Itens", "Quantidade total"
    };

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final PedidoRepository pedidoRepository;

    public RelatorioPedidoService(PedidoRepository pedidoRepository) {
        this.pedidoRepository = pedidoRepository;
    }

    /**
     * Linhas do relatorio de pedidos de um proprietario. Todos os filtros sao
     * opcionais: sem eles, o relatorio traz todos os pedidos (CDU-25).
     */
    @Transactional(readOnly = true)
    public List<String[]> montarLinhas(
            UUID proprietarioId,
            LocalDate dataInicio,
            LocalDate dataFim,
            TipoPedido tipoPedido,
            StatusPedido status) {

        List<Pedido> pedidos = pedidoRepository.findAllByProprietarioRecebedorId(proprietarioId);

        List<String[]> linhas = new ArrayList<>();

        for (Pedido pedido : pedidos) {
            if (passaNosFiltros(pedido, dataInicio, dataFim, tipoPedido, status)) {
                linhas.add(montarLinha(pedido));
            }
        }

        return linhas;
    }

    private boolean passaNosFiltros(
            Pedido pedido,
            LocalDate dataInicio,
            LocalDate dataFim,
            TipoPedido tipoPedido,
            StatusPedido status) {

        LocalDate dataDoPedido = pedido.getDataPedido() == null ? null : pedido.getDataPedido().toLocalDate();

        if (dataInicio != null && (dataDoPedido == null || dataDoPedido.isBefore(dataInicio))) {
            return false;
        }

        if (dataFim != null && (dataDoPedido == null || dataDoPedido.isAfter(dataFim))) {
            return false;
        }

        if (tipoPedido != null && pedido.getTipoPedido() != tipoPedido) {
            return false;
        }

        if (status != null && pedido.getStatus() != status) {
            return false;
        }

        return true;
    }

    private String[] montarLinha(Pedido pedido) {
        return new String[] {
                data(pedido),
                texto(pedido.getTipoPedido()),
                texto(pedido.getStatus()),
                solicitante(pedido),
                itens(pedido),
                numero(quantidadeTotal(pedido))
        };
    }

    private String data(Pedido pedido) {
        if (pedido.getDataPedido() == null) {
            return "-";
        }
        return pedido.getDataPedido().format(DATA);
    }

    // Pedido do site público não tem conta: quem pediu é o comprador
    private String solicitante(Pedido pedido) {
        if (pedido.getUsuarioSolicitante() != null) {
            return pedido.getUsuarioSolicitante().getNome();
        }
        if (pedido.getComprador() != null) {
            return pedido.getComprador().getNome();
        }
        return "-";
    }

    // Junta os itens em um texto so: "2 x Feijao Crioulo, 1 x Milho"
    private String itens(Pedido pedido) {

        if (pedido.getItens() == null || pedido.getItens().isEmpty()) {
            return "-";
        }

        List<String> descricoes = new ArrayList<>();

        for (Itens item : pedido.getItens()) {
            String nomeProduto = "produto";

            if (item.getProduto() != null && item.getProduto().getNomePopular() != null) {
                nomeProduto = item.getProduto().getNomePopular();
            }

            descricoes.add(numero(item.getQuantidade()) + " x " + nomeProduto);
        }

        return String.join(", ", descricoes);
    }

    private Double quantidadeTotal(Pedido pedido) {

        if (pedido.getItens() == null) {
            return 0.0;
        }

        double total = 0.0;

        for (Itens item : pedido.getItens()) {
            if (item.getQuantidade() != null) {
                total = total + item.getQuantidade();
            }
        }

        return total;
    }

    private String texto(Object valor) {
        if (valor == null) {
            return "-";
        }
        return valor.toString();
    }

    private String numero(Double valor) {
        if (valor == null) {
            return "-";
        }
        if (valor == Math.floor(valor)) {
            return String.valueOf(valor.longValue());
        }
        return String.valueOf(valor);
    }
}
