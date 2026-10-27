package com.sementelivre.backend.controller;

import io.swagger.v3.oas.annotations.tags.Tag;

import com.sementelivre.backend.dto.UsuarioResponseDTO;
import com.sementelivre.backend.service.UsuarioService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Contas de login. A conta nasce junto com o proprietario (POST /auth/cadastrar
 * ou POST /api/proprietarios); os dados pessoais sao editados pelos endpoints
 * de pessoa/proprietario.
 */
@RestController
@RequestMapping("/api/usuarios")
@Tag(name = "Usuários", description = "Contas de login (admin).")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    // So admin lista as contas; a resposta traz CPF, RG e endereco.
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UsuarioResponseDTO>> listar() {
        List<UsuarioResponseDTO> usuarios = usuarioService.listarTodos().stream()
                .map(UsuarioResponseDTO::fromEntity)
                .toList();
        return ResponseEntity.ok(usuarios);
    }

    // Cada um consulta so a propria conta; admin consulta qualquer uma.
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or #id == principal.id")
    public ResponseEntity<UsuarioResponseDTO> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(UsuarioResponseDTO.fromEntity(usuarioService.buscarPorId(id)));
    }

    /** Remove so a conta de login; a pessoa continua cadastrada. So admin (#60). */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deletar(@PathVariable UUID id) {
        usuarioService.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
