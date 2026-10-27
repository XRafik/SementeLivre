package com.sementelivre.backend.util;

import java.io.StringWriter;
import java.util.List;

import com.opencsv.CSVWriter;

import com.sementelivre.backend.exception.RelatorioException;

/**
 * Monta o CSV do relatorio com OpenCSV (issue #95).
 *
 * <p>Usa ponto e virgula como separador, que e o que o Excel em portugues
 * espera; com virgula, a planilha abre tudo em uma coluna so.</p>
 */
public final class RelatorioCsv {

    private static final char SEPARADOR = ';';

    private RelatorioCsv() {
    }

    public static byte[] gerar(String[] cabecalhos, List<String[]> linhas) {

        StringWriter texto = new StringWriter();

        try (CSVWriter writer = new CSVWriter(
                texto,
                SEPARADOR,
                CSVWriter.DEFAULT_QUOTE_CHARACTER,
                CSVWriter.DEFAULT_ESCAPE_CHARACTER,
                CSVWriter.DEFAULT_LINE_END)) {

            writer.writeNext(cabecalhos);

            for (String[] linha : linhas) {
                writer.writeNext(linha);
            }
        } catch (Exception e) {
            throw new RelatorioException("Nao foi possivel gerar o CSV do relatorio.", e);
        }

        // UTF-8 com BOM: sem isso o Excel mostra "Feijao Crioulo" com acento quebrado
        String comBom = "﻿" + texto.toString();

        return comBom.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
}
