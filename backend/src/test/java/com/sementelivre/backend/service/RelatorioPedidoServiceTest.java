package com.sementelivre.backend.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sementelivre.backend.entity.Comprador;
import com.sementelivre.backend.entity.Itens;
import com.sementelivre.backend.entity.Pedido;
import com.sementelivre.backend.entity.Produto;
import com.sementelivre.backend.entity.Usuario;
import com.sementelivre.backend.entity.enums.StatusPedido;
import com.sementelivre.backend.entity.enums.TipoPedido;
import com.sementelivre.backend.repository.PedidoRepository;

@ExtendWith(MockitoExtension.class)
class RelatorioPedidoServiceTest {

    @Mock
    private PedidoRepository pedidoRepository;

    private RelatorioPedidoService relatorioPedidoService;

    private UUID proprietarioId;
    private Pedido pedidoTrocaConfirmado;
    private Pedido pedidoVendaCancelado;

    @BeforeEach
    void setUp() {
        relatorioPedidoService = new RelatorioPedidoService(pedidoRepository);

        proprietarioId = UUID.randomUUID();

        pedidoTrocaConfirmado = pedido(
                LocalDateTime.of(2026, 1, 10, 9, 0), TipoPedido.TROCA, StatusPedido.CONFIRMADO, "Maria Silva");

        pedidoVendaCancelado = pedido(
                LocalDateTime.of(2026, 5, 20, 9, 0), TipoPedido.VENDA, StatusPedido.CANCELADO, "Joao Souza");
    }

    private Pedido pedido(LocalDateTime data, TipoPedido tipo, StatusPedido status, String nomeSolicitante) {

        // lenient: nem todo teste chega a ler o nome do solicitante
        Usuario solicitante = mock(Usuario.class);
        lenient().when(solicitante.getNome()).thenReturn(nomeSolicitante);

        Produto feijao = Produto.builder().nomePopular("Feijao Crioulo").build();

        return Pedido.builder()
                .id(UUID.randomUUID())
                .dataPedido(data)
                .tipoPedido(tipo)
                .status(status)
                .usuarioSolicitante(solicitante)
                .itens(List.of(
                        Itens.builder().produto(feijao).quantidade(2.0).build(),
                        Itens.builder().produto(feijao).quantidade(1.5).build()))
                .build();
    }

    private void repositorioDevolve(Pedido... pedidos) {
        when(pedidoRepository.findAllByProprietarioRecebedorId(proprietarioId))
                .thenReturn(List.of(pedidos));
    }

    private List<String[]> linhas(LocalDate inicio, LocalDate fim, TipoPedido tipo, StatusPedido status) {
        return relatorioPedidoService.montarLinhas(proprietarioId, inicio, fim, tipo, status);
    }

    @Test
    void semFiltrosDeveTrazerTodosOsPedidos() {
        repositorioDevolve(pedidoTrocaConfirmado, pedidoVendaCancelado);

        assertEquals(2, linhas(null, null, null, null).size());
    }

    @Test
    void deveFiltrarPorPeriodo() {
        repositorioDevolve(pedidoTrocaConfirmado, pedidoVendaCancelado);

        List<String[]> resultado = linhas(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31), null, null);

        assertEquals(1, resultado.size());
        assertEquals("10/01/2026", resultado.get(0)[0]);
    }

    @Test
    void deveFiltrarPorTipoDePedido() {
        repositorioDevolve(pedidoTrocaConfirmado, pedidoVendaCancelado);

        List<String[]> resultado = linhas(null, null, TipoPedido.VENDA, null);

        assertEquals(1, resultado.size());
        assertEquals("VENDA", resultado.get(0)[1]);
    }

    @Test
    void deveFiltrarPorStatus() {
        repositorioDevolve(pedidoTrocaConfirmado, pedidoVendaCancelado);

        List<String[]> resultado = linhas(null, null, null, StatusPedido.CONFIRMADO);

        assertEquals(1, resultado.size());
        assertEquals("CONFIRMADO", resultado.get(0)[2]);
    }

    @Test
    void linhaDeveTerSolicitanteItensEQuantidadeTotal() {
        repositorioDevolve(pedidoTrocaConfirmado);

        String[] linha = linhas(null, null, null, null).get(0);

        assertEquals(RelatorioPedidoService.CABECALHOS.length, linha.length);
        assertEquals("Maria Silva", linha[3]);
        assertTrue(linha[4].contains("2 x Feijao Crioulo"), linha[4]);
        assertTrue(linha[4].contains("1.5 x Feijao Crioulo"), linha[4]);
        assertEquals("3.5", linha[5]);
    }

    @Test
    void pedidoDoSiteSemContaDeveMostrarOComprador() {
        Pedido doSite = pedido(LocalDateTime.of(2026, 10, 12, 10, 0), TipoPedido.VENDA, StatusPedido.PENDENTE, null);
        doSite.setUsuarioSolicitante(null);
        doSite.setComprador(Comprador.builder().nome("Ana Souza").email("ana@exemplo.com").build());
        repositorioDevolve(doSite);

        assertEquals("Ana Souza", linhas(null, null, null, null).get(0)[3]);
    }

    @Test
    void semDadosDeveDevolverListaVazia() {
        when(pedidoRepository.findAllByProprietarioRecebedorId(proprietarioId)).thenReturn(List.of());

        assertTrue(linhas(null, null, null, null).isEmpty());
    }
}
