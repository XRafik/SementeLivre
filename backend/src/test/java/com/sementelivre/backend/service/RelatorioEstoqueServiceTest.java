package com.sementelivre.backend.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sementelivre.backend.entity.Estoque;
import com.sementelivre.backend.entity.Produto;
import com.sementelivre.backend.entity.enums.Disponibilidade;
import com.sementelivre.backend.entity.enums.EspecieGeral;
import com.sementelivre.backend.entity.enums.Pesagem;
import com.sementelivre.backend.repository.EstoqueRelatorioRepository;

@ExtendWith(MockitoExtension.class)
class RelatorioEstoqueServiceTest {

    @Mock
    private EstoqueRelatorioRepository estoqueRelatorioRepository;

    private RelatorioEstoqueService relatorioEstoqueService;

    private UUID proprietarioId;
    private Estoque feijao;
    private Estoque milho;

    @BeforeEach
    void setUp() {
        relatorioEstoqueService = new RelatorioEstoqueService(estoqueRelatorioRepository);

        proprietarioId = UUID.randomUUID();

        feijao = estoque("Feijao Crioulo", EspecieGeral.FEIJAO, 10.0, Disponibilidade.PARA_TROCA);
        milho = estoque("Milho", EspecieGeral.MILHO, 2.5, Disponibilidade.INDISPONIVEL);
    }

    private Estoque estoque(String nome, EspecieGeral especie, Double quantidade, Disponibilidade disponibilidade) {

        Produto produto = Produto.builder()
                .nomePopular(nome)
                .especie(especie)
                .build();

        return Estoque.builder()
                .produto(produto)
                .quantidade(quantidade)
                .preco(5.0)
                .tipoPesagem(Pesagem.KG)
                .disponibilidade(disponibilidade)
                .dataUltimaAtualizacao(LocalDateTime.of(2026, 3, 15, 10, 0))
                .build();
    }

    private void repositorioDevolve(Estoque... estoques) {
        when(estoqueRelatorioRepository.findByProprietarioIdOrderByProdutoNomePopular(proprietarioId))
                .thenReturn(List.of(estoques));
    }

    @Test
    void semFiltrosDeveTrazerTodosOsEstoques() {
        repositorioDevolve(feijao, milho);

        List<String[]> linhas = relatorioEstoqueService.montarLinhas(proprietarioId, null, null);

        assertEquals(2, linhas.size());
    }

    @Test
    void deveFiltrarPorEspecie() {
        repositorioDevolve(feijao, milho);

        List<String[]> linhas = relatorioEstoqueService.montarLinhas(proprietarioId, EspecieGeral.FEIJAO, null);

        assertEquals(1, linhas.size());
        assertEquals("Feijao Crioulo", linhas.get(0)[0]);
    }

    @Test
    void deveFiltrarPorDisponibilidade() {
        repositorioDevolve(feijao, milho);

        List<String[]> linhas =
                relatorioEstoqueService.montarLinhas(proprietarioId, null, Disponibilidade.INDISPONIVEL);

        assertEquals(1, linhas.size());
        assertEquals("Milho", linhas.get(0)[0]);
    }

    @Test
    void linhaDeveTerUmValorParaCadaCabecalho() {
        repositorioDevolve(feijao);

        String[] linha = relatorioEstoqueService.montarLinhas(proprietarioId, null, null).get(0);

        assertEquals(RelatorioEstoqueService.CABECALHOS.length, linha.length);
        assertEquals("10", linha[2], "quantidade inteira sai sem casa decimal");
        assertEquals("KG", linha[3]);
        assertEquals("15/03/2026", linha[6]);
    }

    @Test
    void quantidadeComCasaDecimalDeveSerMantida() {
        repositorioDevolve(milho);

        String[] linha = relatorioEstoqueService.montarLinhas(proprietarioId, null, null).get(0);

        assertEquals("2.5", linha[2]);
    }

    @Test
    void semDadosDeveDevolverListaVazia() {
        when(estoqueRelatorioRepository.findByProprietarioIdOrderByProdutoNomePopular(proprietarioId))
                .thenReturn(List.of());

        assertTrue(relatorioEstoqueService.montarLinhas(proprietarioId, null, null).isEmpty());
    }
}
