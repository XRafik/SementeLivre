package com.sementelivre.backend.util;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Testes dos arquivos gerados (issue #95): confere se o PDF e o CSV saem no
 * formato certo, inclusive quando nao ha nenhum dado.
 */
class RelatorioArquivoTest {

    private static final String[] CABECALHOS = {"Produto", "Quantidade"};

    private static final List<String[]> LINHAS = List.of(
            new String[] {"Feijao Crioulo", "10"},
            new String[] {"Milho", "2.5"});

    private String csvComoTexto(byte[] arquivo) {
        return new String(arquivo, StandardCharsets.UTF_8);
    }

    @Test
    void pdfDeveComecarComAAssinaturaDeUmArquivoPdf() {
        byte[] arquivo = RelatorioPdf.gerar("Relatorio de teste", CABECALHOS, LINHAS);

        String inicio = new String(arquivo, 0, 4, StandardCharsets.ISO_8859_1);

        assertEquals("%PDF", inicio);
        assertTrue(arquivo.length > 0);
    }

    @Test
    void pdfSemDadosNaoDeveQuebrar() {
        byte[] arquivo = RelatorioPdf.gerar("Relatorio vazio", CABECALHOS, List.of());

        assertEquals("%PDF", new String(arquivo, 0, 4, StandardCharsets.ISO_8859_1));
    }

    @Test
    void csvDeveTerCabecalhosEmPortuguesSeparadosPorPontoEVirgula() {
        String texto = csvComoTexto(RelatorioCsv.gerar(CABECALHOS, LINHAS));

        assertTrue(texto.contains("\"Produto\";\"Quantidade\""), texto);
    }

    @Test
    void csvDeveTerUmaLinhaPorRegistro() {
        String texto = csvComoTexto(RelatorioCsv.gerar(CABECALHOS, LINHAS));

        // 1 cabecalho + 2 registros
        assertEquals(3, texto.strip().split("\n").length);
        assertTrue(texto.contains("Feijao Crioulo"), texto);
    }

    @Test
    void csvDeveComecarComBomParaOExcelLerOsAcentos() {
        String texto = csvComoTexto(RelatorioCsv.gerar(CABECALHOS, LINHAS));

        assertTrue(texto.startsWith("﻿"));
    }

    @Test
    void csvSemDadosDeveTrazerSoOCabecalho() {
        String texto = csvComoTexto(RelatorioCsv.gerar(CABECALHOS, List.of()));

        assertEquals(1, texto.strip().split("\n").length);
    }
}
