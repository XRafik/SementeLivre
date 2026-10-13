package com.sementelivre.backend.controller;

import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sementelivre.backend.dto.RelatorioRequestDTO;
import com.sementelivre.backend.dto.RelatorioResponseDTO;
import com.sementelivre.backend.service.RelatorioService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/relatorios")
@Tag(name = "Relatórios", description = "Histórico de relatórios emitidos.")
public class RelatorioController {

    private final RelatorioService relatorioService;

    public RelatorioController(RelatorioService relatorioService) {
        this.relatorioService = relatorioService;
    }

    // CREATE
    @PostMapping
    public ResponseEntity<RelatorioResponseDTO> criar(
            @Valid @RequestBody RelatorioRequestDTO relatorio) {

        return ResponseEntity.status(HttpStatus.CREATED).body(
                relatorioService.criar(relatorio)
        );
    }

    // READ - todos
    @GetMapping
    public ResponseEntity<List<RelatorioResponseDTO>> listar() {

        return ResponseEntity.ok(
                relatorioService.listar()
        );
    }

    // READ - por ID
    @GetMapping("/{id}")
    public ResponseEntity<RelatorioResponseDTO> buscarPorId(
            @PathVariable UUID id) {

        return ResponseEntity.ok(
                relatorioService.buscarPorId(id)
        );
    }

    // UPDATE
    @PutMapping("/{id}")
    public ResponseEntity<RelatorioResponseDTO> atualizar(
            @PathVariable UUID id,
            @Valid @RequestBody RelatorioRequestDTO relatorio) {

        return ResponseEntity.ok(
                relatorioService.atualizar(id, relatorio)
        );
    }

    // DELETE
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable UUID id) {

        relatorioService.deletar(id);

        return ResponseEntity.noContent().build();
    }
}
