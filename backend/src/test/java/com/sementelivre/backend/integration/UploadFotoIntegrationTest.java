package com.sementelivre.backend.integration;

import static org.hamcrest.Matchers.endsWith;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;

/**
 * O upload de foto só aceita imagem de verdade (#199). Antes valia o
 * Content-Type mandado pelo cliente e a extensão do nome: um SVG com script
 * ficava público em /uploads e rodava na origem do site.
 */
class UploadFotoIntegrationTest extends AbstractAuthIntegrationTest {

    private static final byte[] PNG_1X1 = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=");

    private static final byte[] SVG_COM_SCRIPT =
            "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(document.domain)</script></svg>"
                    .getBytes(StandardCharsets.UTF_8);

    private String enviar(MockMultipartFile foto, String token, int statusEsperado) throws Exception {
        return mockMvc.perform(multipart("/produtos/upload-foto").file(foto)
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().is(statusEsperado))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void svgComScriptDeveSerRecusado() throws Exception {
        String token = loginDeUmProdutor();

        enviar(new MockMultipartFile("foto", "foto.svg", "image/svg+xml", SVG_COM_SCRIPT), token, 400);
        // Mesmo disfarçado de PNG pelo nome e pelo Content-Type
        String resposta = enviar(new MockMultipartFile("foto", "foto.png", "image/png", SVG_COM_SCRIPT), token, 400);

        assertTrue(resposta.contains("JPEG, PNG ou WebP"), resposta);
    }

    @Test
    void htmlComNomeDeImagemDeveSerRecusado() throws Exception {
        byte[] html = "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(multipart("/produtos/upload-foto")
                        .file(new MockMultipartFile("foto", "foto.jpg", "image/jpeg", html))
                        .header(HttpHeaders.AUTHORIZATION, loginDeUmProdutor()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A foto deve ser JPEG, PNG ou WebP"));
    }

    @Test
    void imagemDeveSerSalvaComAExtensaoDoTipoDetectado() throws Exception {
        // PNG de verdade com nome e Content-Type de outro tipo: vale o conteúdo
        String caminho = enviar(new MockMultipartFile("foto", "foto.html", "text/html", PNG_1X1),
                loginDeUmProdutor(), 200).trim();

        try {
            assertTrue(caminho.startsWith("/uploads/produtos/"), caminho);
            assertTrue(caminho.endsWith(".png"), caminho);

            // Servida com CSP sandbox: aberta direto, não roda script
            mockMvc.perform(get(caminho))
                    .andExpect(status().isOk())
                    .andExpect(content().bytes(PNG_1X1))
                    .andExpect(header().string("Content-Security-Policy", "default-src 'none'; sandbox"))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                    .andExpect(header().string(HttpHeaders.CONTENT_TYPE, endsWith("png")));
        } finally {
            Files.deleteIfExists(Path.of("uploads").resolve(caminho.substring("/uploads/".length())));
        }
    }

    private String loginDeUmProdutor() throws Exception {
        return login(cadastrar().email(), SENHA);
    }
}
