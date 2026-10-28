package com.sementelivre.backend.controller;

import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.sementelivre.backend.dto.ProdutoRequestDTO;
import com.sementelivre.backend.dto.ProdutoResponseDTO;
import com.sementelivre.backend.service.FotoProdutoService;
import com.sementelivre.backend.service.ProdutoService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/produtos")
@Tag(name = "Produtos", description = "Catálogo compartilhado de sementes e mudas, com upload de foto.")
public class ProdutoController {

    private final ProdutoService produtoService;
    private final FotoProdutoService fotoProdutoService;

    public ProdutoController(
        ProdutoService produtoService,
        FotoProdutoService fotoProdutoService) {

    this.produtoService = produtoService;
    this.fotoProdutoService = fotoProdutoService;
}

    // CREATE
    @PostMapping
    public ResponseEntity<ProdutoResponseDTO> criar(
            @Valid @RequestBody ProdutoRequestDTO produto) {

        return ResponseEntity.ok(
                produtoService.criar(produto)
        );
    }

    // READ - todos
    @GetMapping
    public ResponseEntity<List<ProdutoResponseDTO>> listarTodos() {

        return ResponseEntity.ok(
                produtoService.listarTodos()
        );
    }

    // READ - por ID
    @GetMapping("/{id}")
    public ResponseEntity<ProdutoResponseDTO> buscarPorId(
            @PathVariable UUID id) {

        return ResponseEntity.ok(
                produtoService.buscarPorId(id)
        );
    }

    // UPDATE - admin, ou produtor cujo produto não é usado por outro produtor
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @produtoService.podeAlterar(#id, principal.id)")
    public ResponseEntity<ProdutoResponseDTO> atualizar(
            @PathVariable UUID id,
            @Valid @RequestBody ProdutoRequestDTO produto) {

        return ResponseEntity.ok(
                produtoService.atualizar(id, produto)
        );
    }

    // DELETE - mesma regra do UPDATE
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @produtoService.podeAlterar(#id, principal.id)")
    public ResponseEntity<Void> excluir(@PathVariable UUID id) {

        produtoService.excluir(id);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/upload-foto")
    public ResponseEntity<String> uploadFoto(
        @RequestParam("foto") MultipartFile foto) {

        String url = fotoProdutoService.salvar(foto);

        return ResponseEntity.ok(url);
    }
}