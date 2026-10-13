package com.sementelivre.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sementelivre.backend.entity.Comprador;
import com.sementelivre.backend.entity.Itens;
import com.sementelivre.backend.entity.Pedido;
import com.sementelivre.backend.entity.Produto;
import com.sementelivre.backend.entity.Proprietario;
import com.sementelivre.backend.entity.Usuario;
import com.sementelivre.backend.entity.enums.TipoPedido;

class CopiaDoPedidoEmailTest {

    private Pedido pedido;

    @BeforeEach
    void setUp() {
        Proprietario produtor = new Proprietario();
        produtor.setNome("Dona Rosa");

        pedido = new Pedido();
        pedido.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        pedido.setTipoPedido(TipoPedido.VENDA);
        pedido.setDataPedido(LocalDateTime.of(2026, 10, 12, 14, 30));
        pedido.setProprietarioRecebedor(produtor);
        pedido.adicionarItem(Itens.builder()
                .produto(Produto.builder().nomePopular("Feijão Crioulo").build())
                .quantidade(2.0)
                .precoUnitario(15.0)
                .build());
    }

    @Test
    void deveIrParaOEmailDoCompradorDoSite() {
        pedido.setComprador(Comprador.builder()
                .nome("Ana Souza").email("ana@exemplo.com").telefone("31 90000-0000").build());
        pedido.setMensagemOpcional("Entregar na feira");

        CopiaDoPedidoEmail copia = CopiaDoPedidoEmail.de(pedido).orElseThrow();
        String texto = copia.texto();

        assertEquals("ana@exemplo.com", copia.para());
        assertEquals("Semente Livre - Cópia do seu pedido", copia.assunto());
        assertTrue(texto.contains("Olá, Ana Souza!"), texto);
        assertTrue(texto.contains("Pedido: 00000000-0000-0000-0000-000000000001"), texto);
        assertTrue(texto.contains("Data: 12/10/2026 às 14:30"), texto);
        assertTrue(texto.contains("Tipo: Venda"), texto);
        assertTrue(texto.contains("Produtor: Dona Rosa"), texto);
        assertTrue(texto.contains("- 2 x Feijão Crioulo"), texto);
        assertTrue(texto.contains("Total: R$"), texto);
        assertTrue(texto.contains("Telefone: 31 90000-0000"), texto);
        assertTrue(texto.contains("Entregar na feira"), texto);
    }

    @Test
    void semEmailDoCompradorDeveIrParaAContaDoSolicitante() {
        pedido.setComprador(Comprador.builder().nome("Associação").telefone("3299990000").build());
        pedido.setUsuarioSolicitante(usuario("João", "joao@exemplo.com"));

        CopiaDoPedidoEmail copia = CopiaDoPedidoEmail.de(pedido).orElseThrow();

        assertEquals("joao@exemplo.com", copia.para());
        assertTrue(copia.texto().contains("Olá, João!"), copia.texto());
    }

    @Test
    void semNenhumEmailNaoHaCopia() {
        pedido.setComprador(Comprador.builder().nome("Associação").build());

        assertTrue(CopiaDoPedidoEmail.de(pedido).isEmpty());
    }

    @Test
    void semPrecoNaoMostraTotal() {
        pedido.getItens().get(0).setPrecoUnitario(null);
        pedido.setTipoPedido(TipoPedido.DOACAO);
        pedido.setComprador(Comprador.builder().nome("Ana").email("ana@exemplo.com").build());

        String texto = CopiaDoPedidoEmail.de(pedido).orElseThrow().texto();

        assertTrue(texto.contains("Tipo: Doação"), texto);
        assertFalse(texto.contains("Total:"), texto);
    }

    private Usuario usuario(String nome, String email) {
        Proprietario pessoa = new Proprietario();
        pessoa.setNome(nome);
        pessoa.setEmail(email);
        Usuario usuario = new Usuario();
        usuario.setPessoa(pessoa);
        return usuario;
    }
}
