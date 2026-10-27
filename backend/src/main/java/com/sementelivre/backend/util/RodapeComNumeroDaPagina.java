package com.sementelivre.backend.util;

import java.awt.Color;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfWriter;

/**
 * Escreve "Pagina X de Y" no rodape de cada pagina do PDF (issue #95).
 *
 * <p>O total de paginas so e conhecido no fim do documento. Por isso reservamos
 * um espacinho (PdfTemplate) em cada pagina e escrevemos o total nele apenas
 * quando o documento fecha. E o jeito padrao de fazer isso no OpenPDF.</p>
 */
public class RodapeComNumeroDaPagina extends PdfPageEventHelper {

    private static final float TAMANHO = 8f;

    // A fonte base e necessaria para escrever direto no template do total de paginas
    private static final BaseFont FONTE_BASE = baseFontHelvetica();

    private static final Font FONTE_RODAPE = new Font(FONTE_BASE, TAMANHO, Font.NORMAL, Color.GRAY);

    private PdfTemplate espacoDoTotal;

    private static BaseFont baseFontHelvetica() {
        try {
            return BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
        } catch (Exception e) {
            throw new IllegalStateException("Nao foi possivel carregar a fonte do rodape.", e);
        }
    }

    @Override
    public void onOpenDocument(PdfWriter writer, Document documento) {
        espacoDoTotal = writer.getDirectContent().createTemplate(30, 12);
    }

    @Override
    public void onEndPage(PdfWriter writer, Document documento) {

        Rectangle pagina = documento.getPageSize();
        PdfContentByte conteudo = writer.getDirectContent();

        Phrase texto = new Phrase("Pagina " + writer.getPageNumber() + " de ", FONTE_RODAPE);

        float x = pagina.getWidth() / 2;
        float y = pagina.getBottom() + 20;

        ColumnText.showTextAligned(conteudo, Element.ALIGN_RIGHT, texto, x, y, 0);
        conteudo.addTemplate(espacoDoTotal, x + 2, y);
    }

    @Override
    public void onCloseDocument(PdfWriter writer, Document documento) {

        // Agora sim sabemos quantas paginas o documento tem.
        espacoDoTotal.beginText();
        espacoDoTotal.setFontAndSize(FONTE_BASE, TAMANHO);
        espacoDoTotal.showText(String.valueOf(writer.getPageNumber()));
        espacoDoTotal.endText();
    }
}
