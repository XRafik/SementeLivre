package com.sementelivre.backend.controller;

import io.swagger.v3.oas.annotations.tags.Tag;


import com.sementelivre.backend.dto.ComunidadeRequestDTO;
import com.sementelivre.backend.dto.ComunidadeResponseDTO;
import com.sementelivre.backend.service.ComunidadeService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/comunidades")
@Tag(name = "Comunidades", description = "Solicitação de comunidade pelo proprietário; aprovação, rejeição e edição pelo admin.")
public class ComunidadeController {

    private final ComunidadeService comunidadeService;


    public ComunidadeController(ComunidadeService comunidadeService) {
        this.comunidadeService = comunidadeService;
    }

    // CREATE
    @PostMapping
    public ResponseEntity<ComunidadeResponseDTO> criar(
            @Valid @RequestBody ComunidadeRequestDTO comunidade){

        return ResponseEntity.ok(
                comunidadeService.criar(comunidade)
        );
    }

    // READ - todos
    @GetMapping
    public ResponseEntity<List<ComunidadeResponseDTO>> listar(){

        return ResponseEntity.ok(
                comunidadeService.listar()
        );
    }

    // READ - por id
    @GetMapping("/{id}")
    public  ResponseEntity<ComunidadeResponseDTO> buscarPorId(
            @PathVariable UUID id){

        return ResponseEntity.ok(
                comunidadeService.buscarPorId(id)
        );
    }

    // UPDATE
    @PutMapping("/{id}")
    public ResponseEntity<ComunidadeResponseDTO> atualizar(
            @PathVariable UUID id,
            @Valid @RequestBody ComunidadeRequestDTO comunidade){

        return ResponseEntity.ok(
                comunidadeService.atualizar(id,comunidade));
    }

    // DELETE
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(
            @PathVariable UUID id){
        comunidadeService.deletar(id);

        return ResponseEntity.noContent().build();
    }


    // APPROVED
    @PatchMapping("/{id}/aprovar")
    public ResponseEntity<ComunidadeResponseDTO> aprovar(
            @PathVariable UUID id){

        return ResponseEntity.ok(
                comunidadeService.aprovar(id)
        );
    }

    // REJECTED
    @PatchMapping("/{id}/rejeitar")
    public ResponseEntity<ComunidadeResponseDTO> rejeitar(
            @PathVariable UUID id){

        return ResponseEntity.ok(
                comunidadeService.rejeitar(id)
        );
    }

}
