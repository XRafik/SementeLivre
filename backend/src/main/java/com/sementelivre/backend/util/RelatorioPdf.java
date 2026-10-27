package com.sementelivre.backend.util;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import com.sementelivre.backend.exception.RelatorioException;

/**
 * Monta o PDF do relatorio com OpenPDF (issue #95).
 *
 * <p>Segue o mesmo formato do prototipo da Fase 0 (#16): cabecalho com o nome
 * do projeto e o titulo, data de geracao, tabela com os dados e rodape com
 * "Pagina X de Y".</p>
 */
public final class RelatorioPdf {

    private static final Font FONTE_PROJETO = new Font(Font.HELVETICA, 12, Font.BOLD, Color.DARK_GRAY);
    private static final Font FONTE_TITULO = new Font(Font.HELVETICA, 18, Font.BOLD);
    private static final Font FONTE_DATA = new Font(Font.HELVETICA, 10, Font.ITALIC, Color.GRAY);
    private static final Font FONTE_CABECALHO = new Font(Font.HELVETICA, 10, Font.BOLD, Color.WHITE);
    private static final Font FONTE_LINHA = new Font(Font.HELVETICA, 9);

    private RelatorioPdf() {
    }

    /**
     * @param titulo     titulo que aparece no topo do documento
     * @param cabecalhos nomes das colunas, em portugues
     * @param linhas     dados ja formatados como texto
     */
    public static byte[] gerar(String titulo, String[] cabecalhos, List<String[]> linhas) {

        Document documento = new Document(PageSize.A4.rotate(), 36, 36, 54, 54);
        ByteArrayOutputStream saida = new ByteArrayOutputStream();

        try {
            PdfWriter writer = PdfWriter.getInstance(documento, saida);
            writer.setPageEvent(new RodapeComNumeroDaPagina());

            documento.open();
            adicionarCabecalho(documento, titulo);

            if (linhas.isEmpty()) {
                documento.add(new Paragraph("Nenhum registro encontrado para os filtros informados.", FONTE_LINHA));
            } else {
                documento.add(montarTabela(cabecalhos, linhas));
            }

            documento.close();
        } catch (Exception e) {
            throw new RelatorioException("Nao foi possivel gerar o PDF do relatorio.", e);
        }

        return saida.toByteArray();
    }

    private static void adicionarCabecalho(Document documento, String titulo) {

        Paragraph projeto = new Paragraph("Semente Livre", FONTE_PROJETO);
        projeto.setAlignment(Element.ALIGN_CENTER);
        documento.add(projeto);

        Paragraph paragrafoTitulo = new Paragraph(titulo, FONTE_TITULO);
        paragrafoTitulo.setAlignment(Element.ALIGN_CENTER);
        documento.add(paragrafoTitulo);

        String dataFormatada = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy 'as' HH:mm"));

        Paragraph data = new Paragraph("Gerado em: " + dataFormatada, FONTE_DATA);
        data.setAlignment(Element.ALIGN_CENTER);
        data.setSpacingAfter(20);
        documento.add(data);
    }

    private static PdfPTable montarTabela(String[] cabecalhos, List<String[]> linhas) {

        PdfPTable tabela = new PdfPTable(cabecalhos.length);
        tabela.setWidthPercentage(100);
        tabela.setHeaderRows(1);

        for (String cabecalho : cabecalhos) {
            PdfPCell celula = new PdfPCell(new Phrase(cabecalho, FONTE_CABECALHO));
            celula.setBackgroundColor(new Color(60, 120, 60));
            celula.setPadding(6);
            tabela.addCell(celula);
        }

        for (String[] linha : linhas) {
            for (String valor : linha) {
                PdfPCell celula = new PdfPCell(new Phrase(valor, FONTE_LINHA));
                celula.setPadding(5);
                tabela.addCell(celula);
            }
        }

        return tabela;
    }
}
