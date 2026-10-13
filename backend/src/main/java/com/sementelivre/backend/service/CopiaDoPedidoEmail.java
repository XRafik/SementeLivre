package com.sementelivre.backend.service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

import com.sementelivre.backend.entity.Comprador;
import com.sementelivre.backend.entity.Itens;
import com.sementelivre.backend.entity.Pedido;
import com.sementelivre.backend.entity.Usuario;
import com.sementelivre.backend.entity.enums.TipoPedido;

/**
 * Cópia do pedido enviada por e-mail a quem fez o pedido.
 *
 * É montada dentro da transação que registra o pedido (as associações lazy
 * ainda estão acessíveis) e só é enviada depois do commit.
 */
public record CopiaDoPedidoEmail(String para, String assunto, String texto) {

    private static final DateTimeFormatter DATA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");

    private static final NumberFormat REAIS =
            NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"));

    /**
     * Destinatário: o e-mail do comprador (pedido do site) ou, na falta dele,
     * o da conta de quem registrou o pedido no app. Sem nenhum dos dois não
     * há para quem mandar.
     */
    public static Optional<CopiaDoPedidoEmail> de(Pedido pedido) {

        Comprador comprador = pedido.getComprador();
        Usuario solicitante = pedido.getUsuarioSolicitante();

        String para;
        String nome;

        if (comprador != null && temTexto(comprador.getEmail())) {
            para = comprador.getEmail();
            nome = comprador.getNome();
        } else if (solicitante != null && temTexto(solicitante.getEmail())) {
            para = solicitante.getEmail();
            nome = solicitante.getNome();
        } else {
            return Optional.empty();
        }

        return Optional.of(new CopiaDoPedidoEmail(
                para,
                "Semente Livre - Cópia do seu pedido",
                montarTexto(pedido, nome)));
    }

    private static String montarTexto(Pedido pedido, String nome) {

        StringBuilder texto = new StringBuilder();

        texto.append("Olá, ").append(nome).append("!\n\n")
                .append("Recebemos o seu pedido. Esta é uma cópia para você guardar.\n\n")
                .append("Pedido: ").append(pedido.getId()).append('\n')
                .append("Data: ").append(pedido.getDataPedido().format(DATA)).append('\n')
                .append("Tipo: ").append(rotulo(pedido.getTipoPedido())).append('\n')
                .append("Situação: aguardando o produtor\n");

        String produtor = pedido.getProprietarioRecebedor() != null
                ? pedido.getProprietarioRecebedor().getNome()
                : null;
        if (temTexto(produtor)) {
            texto.append("Produtor: ").append(produtor).append('\n');
        }

        String comunidade = comunidadeDoPedido(pedido);
        if (temTexto(comunidade)) {
            texto.append("Comunidade: ").append(comunidade).append('\n');
        }

        texto.append("\nItens:\n");

        BigDecimal total = BigDecimal.ZERO;
        boolean temPreco = false;

        for (Itens item : pedido.getItens()) {
            texto.append("- ").append(formatarQuantidade(item.getQuantidade()))
                    .append(" x ").append(item.getProduto().getNomePopular());

            if (item.getPrecoUnitario() != null) {
                BigDecimal subtotal = BigDecimal.valueOf(item.getPrecoUnitario())
                        .multiply(BigDecimal.valueOf(item.getQuantidade()));
                total = total.add(subtotal);
                temPreco = true;
                texto.append(" (").append(REAIS.format(item.getPrecoUnitario())).append(" cada")
                        .append(", subtotal ").append(REAIS.format(subtotal)).append(')');
            }
            texto.append('\n');
        }

        if (temPreco) {
            texto.append("Total: ").append(REAIS.format(total)).append('\n');
        }

        Comprador comprador = pedido.getComprador();
        if (comprador != null) {
            texto.append("\nDados de contato informados:\n")
                    .append("Nome: ").append(comprador.getNome()).append('\n');
            if (temTexto(comprador.getEmail())) {
                texto.append("E-mail: ").append(comprador.getEmail()).append('\n');
            }
            if (temTexto(comprador.getTelefone())) {
                texto.append("Telefone: ").append(comprador.getTelefone()).append('\n');
            }
        }

        if (temTexto(pedido.getMensagemOpcional())) {
            texto.append("\nSua mensagem:\n").append(pedido.getMensagemOpcional()).append('\n');
        }

        texto.append("\nO produtor vai analisar o pedido e entrar em contato.\n\n")
                .append("Semente Livre");

        return texto.toString();
    }

    private static String comunidadeDoPedido(Pedido pedido) {
        return pedido.getItens().stream()
                .map(item -> item.getProduto().getComunidadeOrigem())
                .filter(comunidade -> comunidade != null)
                .map(comunidade -> comunidade.getNome())
                .findFirst()
                .orElse(null);
    }

    private static String rotulo(TipoPedido tipo) {
        return switch (tipo) {
            case VENDA -> "Venda";
            case TROCA -> "Troca";
            case DOACAO -> "Doação";
        };
    }

    // 10.0 -> "10", 2.50 -> "2.5"
    private static String formatarQuantidade(double quantidade) {
        return BigDecimal.valueOf(quantidade).stripTrailingZeros().toPlainString();
    }

    private static boolean temTexto(String valor) {
        return valor != null && !valor.isBlank();
    }
}
