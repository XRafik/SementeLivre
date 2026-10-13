package com.sementelivre.backend.controller;


import com.sementelivre.backend.dto.PropriedadeRequestDTO;
import com.sementelivre.backend.dto.PropriedadeResponseDTO;
import com.sementelivre.backend.entity.Usuario;
import com.sementelivre.backend.service.PropriedadeService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/propriedades")
public class PropriedadeController {

    private final PropriedadeService propriedadeService;

    public PropriedadeController(PropriedadeService propriedadeService) {
        this.propriedadeService = propriedadeService;
    }

    // CREATE
    // Proprietário só cadastra propriedade para si mesmo
    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or #propriedade.proprietarioId() == principal.id")
    public ResponseEntity<PropriedadeResponseDTO> criar(
            @Valid @RequestBody PropriedadeRequestDTO propriedade){

        return ResponseEntity.ok(
                propriedadeService.criar(propriedade)
        );
    }

    // READ - admin lista todas (ou as de um proprietário); o proprietário, só as dele
    @GetMapping
    public ResponseEntity<List<PropriedadeResponseDTO>> listar(
            @RequestParam(required = false) UUID proprietarioId,
            @AuthenticationPrincipal Usuario usuario){

        boolean admin = usuario.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (!admin) {
            return ResponseEntity.ok(propriedadeService.listarPorProprietario(usuario.getId()));
        }
        return ResponseEntity.ok(proprietarioId == null
                ? propriedadeService.listar()
                : propriedadeService.listarPorProprietario(proprietarioId));
    }

    // READ - busca por id (admin ou o dono)
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @propriedadeService.ehDoProprietario(#id, principal.id)")
    public ResponseEntity<PropriedadeResponseDTO> buscarPorId(
            @PathVariable UUID id){

        return ResponseEntity.ok(
                propriedadeService.buscarPorId(id));
    }

    // UPDATE - o dono não pode passar a propriedade para outra pessoa
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or (@propriedadeService.ehDoProprietario(#id, principal.id)"
            + " and #propriedade.proprietarioId() == principal.id)")
    public ResponseEntity<PropriedadeResponseDTO> atualizar(
            @PathVariable UUID id,
            @Valid @RequestBody PropriedadeRequestDTO propriedade){

        return ResponseEntity.ok(
                propriedadeService.atualizar(id,propriedade));
    }

    // DELETE (admin ou o dono)
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @propriedadeService.ehDoProprietario(#id, principal.id)")
    public ResponseEntity<Void> deletar(
            @PathVariable UUID id){
        propriedadeService.deletar(id);

        return ResponseEntity.noContent().build();
    }

}
