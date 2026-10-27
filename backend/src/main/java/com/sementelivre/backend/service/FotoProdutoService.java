package com.sementelivre.backend.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
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

        String contentType = arquivo.getContentType();

        if (contentType == null || !contentType.startsWith("image/")) {
           throw new UploadFotoException("O arquivo enviado deve ser uma imagem");
        }

        try {
            Path pasta = PASTA_UPLOAD.toAbsolutePath().normalize();

            Files.createDirectories(pasta);

            String nomeOriginal = arquivo.getOriginalFilename();

            String extensao = "";

            if (nomeOriginal != null && nomeOriginal.contains(".")) {
                extensao = nomeOriginal.substring(nomeOriginal.lastIndexOf("."));
            }

            String nomeArquivo = UUID.randomUUID() + extensao;

            Path destino = pasta.resolve(nomeArquivo);

            // Copia pelo InputStream em vez de transferTo: o transferTo do Spring
            // resolve o destino contra o diretório temporário do container e
            // falhava com FileNotFoundException.
            try (InputStream in = arquivo.getInputStream()) {
                Files.copy(in, destino, StandardCopyOption.REPLACE_EXISTING);
            }

            return "/uploads/produtos/" + nomeArquivo;

        } catch (IOException e) {
           throw new UploadFotoException("Não foi possível salvar a foto do produto", e);
        }
    }
}