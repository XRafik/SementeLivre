package com.sementelivre.backend.controller;

import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/admin")
@Tag(name = "Administração", description = "Rotas exclusivas do admin.")
public class AdminController {

    /**
     * Endpoint de confirmação de acesso administrativo.
     * Requer a autoridade ROLE_ADMIN — verificação dupla:
     * 1) Via @PreAuthorize (Method Security)
     * 2) Via SecurityConfig (.requestMatchers("/admin/**").hasRole("ADMIN"))
     */
    @GetMapping("/confirmacao")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> confirmacaoAdmin() {
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "mensagem", "Acesso concedido: Usuário é Administrador"
        ));
    }
}
