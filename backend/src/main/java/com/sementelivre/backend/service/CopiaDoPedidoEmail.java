package com.sementelivre.backend.service;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.sementelivre.backend.entity.Comprador;
import com.sementelivre.backend.entity.Itens;
import com.sementelivre.backend.entity.Pedido;
import com.sementelivre.backend.entity.Proprietario;
import com.sementelivre.backend.entity.Usuario;
import com.sementelivre.backend.entity.enums.TipoPedido;

/**
 * E-mail sobre o pedido para quem fez o pedido: a cópia no registro, o aviso
 * de confirmação com o WhatsApp do produtor (#184) e o de recusa da fila (#183).
 *
 * É montado dentro da transação que mexe no pedido (as associações lazy
 * ainda estão acessíveis) e só é enviado depois do commit.
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
        return destinatario(pedido).map(quem -> new CopiaDoPedidoEmail(
                quem.email(),
                "Semente Livre - Cópia do seu pedido",
                montarTexto(pedido, quem.nome())));
    }

    /**
     * Aviso de que o pedido saiu da fila sem ser atendido: o produtor aceitou
     * outro pedido e o saldo que sobrou não cobre este. {@code saldoPorProduto}
     * é o estoque que ficou, para quem pediu saber quanto ainda há.
     */
    public static Optional<CopiaDoPedidoEmail> avisoDeRecusa(Pedido pedido, Map<UUID, Double> saldoPorProduto) {
        return destinatario(pedido).map(quem -> new CopiaDoPedidoEmail(
                quem.email(),
                "Semente Livre - Seu pedido foi recusado",
                montarTextoDeRecusa(pedido, quem.nome(), saldoPorProduto)));
    }

    /**
     * Aviso de que o produtor aceitou o pedido, com o link do WhatsApp dele
     * para os dois combinarem a entrega. Pedido lançado pelo próprio produtor
     * no app não gera aviso: o destinatário seria ele mesmo.
     */
    public static Optional<CopiaDoPedidoEmail> avisoDeConfirmacao(Pedido pedido) {

        Proprietario produtor = pedido.getProprietarioRecebedor();

        return destinatario(pedido)
                .filter(quem -> produtor == null || !quem.email().equalsIgnoreCase(textoOuVazio(produtor.getEmail())))
                .map(quem -> new CopiaDoPedidoEmail(
                        quem.email(),
                        "Semente Livre - Seu pedido foi confirmado",
                        montarTextoDeConfirmacao(pedido, quem.nome(), produtor)));
    }

    /**
     * Link wa.me do telefone: só dígitos e com o DDI do Brasil quando vier só
     * DDD + número. Telefone que não forma um número válido fica sem link.
     */
    static Optional<String> linkWhatsApp(String telefone, String mensagem) {

        if (!temTexto(telefone)) {
            return Optional.empty();
        }

        String digitos = telefone.replaceAll("\\D", "").replaceFirst("^0+", "");
        if (digitos.length() == 10 || digitos.length() == 11) {
            digitos = "55" + digitos;
        }
        if (digitos.length() < 12 || digitos.length() > 13) {
            return Optional.empty();
        }

        String texto = URLEncoder.encode(mensagem, StandardCharsets.UTF_8).replace("+", "%20");
        return Optional.of("https://wa.me/" + digitos + "?text=" + texto);
    }

    private static String montarTextoDeConfirmacao(Pedido pedido, String nome, Proprietario produtor) {

        String nomeProdutor = produtor != null && temTexto(produtor.getNome()) ? produtor.getNome() : "O produtor";

        StringBuilder texto = new StringBuilder();

        texto.append("Olá, ").append(nome).append("!\n\n")
                .append("Boa notícia: ").append(nomeProdutor).append(" confirmou o seu pedido.\n\n")
                .append("Pedido: ").append(pedido.getId()).append('\n')
                .append("Tipo: ").append(rotulo(pedido.getTipoPedido())).append('\n')
                .append("\nItens:\n");

        for (Itens item : pedido.getItens()) {
            texto.append("- ").append(formatarQuantidade(item.getQuantidade()))
                    .append(" x ").append(item.getProduto().getNomePopular()).append('\n');
        }

        String mensagem = "Olá, " + nomeProdutor + "! Sou " + nome
                + " e meu pedido " + pedido.getId() + " foi confirmado no Semente Livre.";
        Optional<String> whatsapp = produtor != null
                ? linkWhatsApp(produtor.getTelefone(), mensagem)
                : Optional.empty();

        if (whatsapp.isPresent()) {
            texto.append("\nPara combinar a entrega, fale com o produtor pelo WhatsApp:\n")
                    .append(whatsapp.get()).append('\n');
        } else if (produtor != null && temTexto(produtor.getEmail())) {
            texto.append("\nPara combinar a entrega, fale com o produtor pelo e-mail:\n")
                    .append(produtor.getEmail()).append('\n');
        } else {
            texto.append("\nO produtor vai entrar em contato pelos dados que você informou.\n");
        }

        texto.append("\nSemente Livre");

        return texto.toString();
    }

    private static String textoOuVazio(String valor) {
        return valor == null ? "" : valor;
    }

    private record Destinatario(String email, String nome) {
    }

    private static Optional<Destinatario> destinatario(Pedido pedido) {

        Comprador comprador = pedido.getComprador();
        Usuario solicitante = pedido.getUsuarioSolicitante();

        if (comprador != null && temTexto(comprador.getEmail())) {
            return Optional.of(new Destinatario(comprador.getEmail(), comprador.getNome()));
        }
        if (solicitante != null && temTexto(solicitante.getEmail())) {
            return Optional.of(new Destinatario(solicitante.getEmail(), solicitante.getNome()));
        }
        return Optional.empty();
    }

    private static String montarTextoDeRecusa(Pedido pedido, String nome, Map<UUID, Double> saldoPorProduto) {

        StringBuilder texto = new StringBuilder();

        texto.append("Olá, ").append(nome).append("!\n\n")
                .append("Infelizmente o seu pedido não pôde ser atendido. O produtor aceitou outro ")
                .append("pedido antes do seu e o estoque que sobrou não cobre a quantidade que você pediu.\n\n")
                .append("Pedido: ").append(pedido.getId()).append('\n')
                .append("Data: ").append(pedido.getDataPedido().format(DATA)).append('\n');

        String produtor = pedido.getProprietarioRecebedor() != null
                ? pedido.getProprietarioRecebedor().getNome()
                : null;
        if (temTexto(produtor)) {
            texto.append("Produtor: ").append(produtor).append('\n');
        }

        texto.append("\nVocê pediu:\n");
        for (Itens item : pedido.getItens()) {
            texto.append("- ").append(formatarQuantidade(item.getQuantidade()))
                    .append(" x ").append(item.getProduto().getNomePopular());

            Double sobrou = saldoPorProduto.get(item.getProduto().getId());
            if (sobrou != null) {
                texto.append(" (disponível agora: ").append(formatarQuantidade(sobrou)).append(')');
            }
            texto.append('\n');
        }

        texto.append("\nSe ainda tiver interesse, faça um novo pedido com a quantidade disponível.\n\n")
                .append("Semente Livre");

        return texto.toString();
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
