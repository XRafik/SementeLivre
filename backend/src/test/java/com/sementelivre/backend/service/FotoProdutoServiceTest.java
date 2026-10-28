package com.sementelivre.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/** O tipo da foto sai dos primeiros bytes do arquivo (#199). */
class FotoProdutoServiceTest {

    private static byte[] bytes(int... valores) {
        byte[] b = new byte[valores.length];
        for (int i = 0; i < valores.length; i++) {
            b[i] = (byte) valores[i];
        }
        return b;
    }

    @Test
    void deveReconhecerJpegPngEWebp() {
        assertEquals(Optional.of(".jpg"), FotoProdutoService.extensaoDaImagem(bytes(0xFF, 0xD8, 0xFF, 0xE0, 0, 0x10)));
        assertEquals(Optional.of(".png"),
                FotoProdutoService.extensaoDaImagem(bytes(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0)));
        assertEquals(Optional.of(".webp"),
                FotoProdutoService.extensaoDaImagem(bytes('R', 'I', 'F', 'F', 0x24, 0, 0, 0, 'W', 'E', 'B', 'P', 'V')));
    }

    @Test
    void naoDeveAceitarOutrosTipos() {
        for (String conteudo : new String[] {
                "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>",
                "<html><body>oi</body></html>",
                "GIF89a",
                "RIFF\0\0\0\0WAVEfmt ",
                "" }) {
            assertTrue(FotoProdutoService.extensaoDaImagem(conteudo.getBytes(StandardCharsets.UTF_8)).isEmpty(), conteudo);
        }
        // Só o começo de uma assinatura
        assertTrue(FotoProdutoService.extensaoDaImagem(bytes(0xFF, 0xD8)).isEmpty());
        assertTrue(FotoProdutoService.extensaoDaImagem(bytes('R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E')).isEmpty());
    }
}
