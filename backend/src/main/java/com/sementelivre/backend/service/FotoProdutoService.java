package com.sementelivre.backend.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.sementelivre.backend.exception.UploadFotoException;

@Service
public class FotoProdutoService {

    private static final Path PASTA_UPLOAD = Paths.get("uploads", "produtos");

    public String salvar(MultipartFile arquivo) {

        if (arquivo == null || arquivo.isEmpty()) {
            throw new UploadFotoException("Arquivo de foto é obrigatório");
        }

        try {
            byte[] dados = arquivo.getBytes();

            // O tipo sai dos bytes do arquivo, não do Content-Type nem do nome, que o
            // cliente escolhe: um SVG ou HTML com script mandado como "image/svg+xml"
            // ficava público em /uploads e rodava na origem do site (#199).
            String extensao = extensaoDaImagem(dados)
                    .orElseThrow(() -> new UploadFotoException("A foto deve ser JPEG, PNG ou WebP"));

            Path pasta = PASTA_UPLOAD.toAbsolutePath().normalize();

            Files.createDirectories(pasta);

            String nomeArquivo = UUID.randomUUID() + extensao;

            Files.write(pasta.resolve(nomeArquivo), dados);

            return "/uploads/produtos/" + nomeArquivo;

        } catch (IOException e) {
           throw new UploadFotoException("Não foi possível salvar a foto do produto", e);
        }
    }

    /** Extensão pela assinatura do arquivo (os primeiros bytes); vazio se não for JPEG, PNG nem WebP. */
    static Optional<String> extensaoDaImagem(byte[] dados) {

        if (comecaCom(dados, 0, 0xFF, 0xD8, 0xFF)) {
            return Optional.of(".jpg");
        }
        if (comecaCom(dados, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return Optional.of(".png");
        }
        // RIFF <tamanho> WEBP
        if (comecaCom(dados, 0, 'R', 'I', 'F', 'F') && comecaCom(dados, 8, 'W', 'E', 'B', 'P')) {
            return Optional.of(".webp");
        }
        return Optional.empty();
    }

    private static boolean comecaCom(byte[] dados, int inicio, int... assinatura) {
        if (dados.length < inicio + assinatura.length) {
            return false;
        }
        byte[] esperado = new byte[assinatura.length];
        for (int i = 0; i < assinatura.length; i++) {
            esperado[i] = (byte) assinatura[i];
        }
        return Arrays.equals(dados, inicio, inicio + assinatura.length, esperado, 0, esperado.length);
    }
}
