package com.sementelivre.backend.controller;

import io.swagger.v3.oas.annotations.tags.Tag;


import com.sementelivre.backend.dto.PropriedadeRequestDTO;
import com.sementelivre.backend.dto.PropriedadeResponseDTO;
import com.sementelivre.backend.service.PropriedadeService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/propriedades")
@Tag(name = "Propriedades", description = "Propriedades rurais do proprietário, vinculadas a uma comunidade.")
public class PropriedadeController {

    private final PropriedadeService propriedadeService;

    public PropriedadeController(PropriedadeService propriedadeService) {
        this.propriedadeService = propriedadeService;
    }

    // CREATE
    @PostMapping
    public ResponseEntity<PropriedadeResponseDTO> criar(
            @Valid @RequestBody PropriedadeRequestDTO propriedade){

        return ResponseEntity.ok(
                propriedadeService.criar(propriedade)
        );
    }

    // READ - todos
    @GetMapping
    public ResponseEntity<List<PropriedadeResponseDTO>> listar(){

        return ResponseEntity.ok(
                propriedadeService.listar());
    }

    // READ - busca por id
    @GetMapping("/{id}")
    public ResponseEntity<PropriedadeResponseDTO> buscarPorId(
            @PathVariable UUID id){

        return ResponseEntity.ok(
                propriedadeService.buscarPorId(id));
    }

    // UPDATE
    @PutMapping("/{id}")
    public ResponseEntity<PropriedadeResponseDTO> atualizar(
            @PathVariable UUID id,
            @Valid @RequestBody PropriedadeRequestDTO propriedade){

        return ResponseEntity.ok(
                propriedadeService.atualizar(id,propriedade));
    }

    // DELETE
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(
            @PathVariable UUID id){
        propriedadeService.deletar(id);

        return ResponseEntity.noContent().build();
    }

}
